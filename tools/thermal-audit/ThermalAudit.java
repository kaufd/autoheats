package com.wt.airconditioner;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

/** Standalone audit of production Java classes; no Android or test doubles from JUnit. */
public final class ThermalAudit {
    private static final long WALL = 1_757_000_000_000L;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        if (args.length > 0 && args[0].equals("simulate")) {
            for (Seat seat : Seat.values()) {
                simulate(seat, false, false);
                simulate(seat, true, false);
                simulate(seat, false, true);
            }
            return;
        }
        if (args.length > 0 && args[0].equals("replay")) {
            replay();
            return;
        }
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = input.readLine()) != null) {
            String[] parts = line.split(" ");
            double[] x = Arrays.stream(parts).skip(1).mapToDouble(Double::parseDouble).toArray();
            if (parts[0].equals("step")) {
                SeatThermalModel.Parameters p = new SeatThermalModel.Parameters();
                p.p=x[5]; p.a=x[6]; p.r=x[7]; p.air=x[8]; p.body=x[9]; p.bodyCelsius=x[10];
                p.duty=new double[]{0,x[11],x[12],x[13]};
                SeatThermalModel.State state=SeatThermalModel.advance(
                        new SeatThermalModel.State(x[0],x[1]),(int)x[2],x[3],p,x[4]);
                System.out.printf("%.12f %.12f%n",state.mat,state.surface);
            } else if (parts[0].equals("choose")) {
                List<SeatThermostat.Scenario> scenarios=new ArrayList<>();
                Seat seat=x[6]==0 ? Seat.DRIVER : Seat.PASSENGER;
                for (SeatThermalModel.Parameters p : SeatObserver.profilesWhileOn(seat)) {
                    scenarios.add(new SeatThermostat.Scenario(
                            new SeatThermalModel.State(x[0],x[1]),p,x[2]));
                }
                SeatThermostat.Decision d=SeatThermostat.choose(scenarios,(int)x[4],x[5],x[3]);
                System.out.println(d.level);
            }
        }
    }

    /** Event-exact replay with unknown initial history, as on a fresh installation. */
    private static void replay() throws Exception {
        SeatObserver observer=null;
        BufferedReader input=new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line=input.readLine()) != null) {
            double[] x=Arrays.stream(line.split(" ")).mapToDouble(Double::parseDouble).toArray();
            long now=(long)x[0];
            if(observer==null) {
                observer=SeatObserver.restore(Seat.DRIVER,null,WALL+now,now,x[1],x[2],(int)x[3],x[4]!=0);
            } else {
                observer.observe(now,(int)x[3],x[4]!=0,x[1],x[2]);
            }
            System.out.printf("%.9f %.9f %.9f %.9f%n",observer.base().low,observer.base().high,
                    observer.bank().low,observer.bank().high);
        }
    }

    private static final class Clock implements Scheduler {
        long now;
        final Map<Runnable,Long> tasks=new LinkedHashMap<>();
        public Cancellation scheduleMillis(long delay, Runnable action) {
            tasks.put(action,now+delay);
            return () -> tasks.remove(action);
        }
        void tick() {
            for (Runnable action : new ArrayList<>(tasks.keySet())) {
                Long due=tasks.get(action);
                if (due != null && due<=now) { tasks.remove(action); action.run(); }
            }
        }
    }

    private static double cabin(long now, boolean warming) {
        double t=now/60_000.0;
        return !warming || t<5 ? -15 : 22-37*Math.exp(-(t-5)/7);
    }

    private static void simulate(Seat seat, boolean unknown, boolean warming) {
        Clock clock=new Clock();
        Map<Seat,String> snapshots=new EnumMap<>(Seat.class);
        Map<Seat,Integer> levels=new EnumMap<>(Seat.class);
        levels.put(seat,0);
        if (!unknown) {
            int histories=SeatObserver.profilesWhileOn(seat).size();
            StringBuilder snapshot=new StringBuilder("1;"+WALL+";0;0;-15");
            for(int i=0;i<=histories;i++) snapshot.append(";-15,-15,-15,-15");
            snapshots.put(seat,snapshot.toString());
        }
        ModelShadow model=new ModelShadow(new ModelShadow.Store() {
            public String load(Seat s) { return snapshots.get(s); }
            public void save(Map<Seat,String> values) { snapshots.putAll(values); }
        }, message -> {});
        ModelHeatDriver[] driver=new ModelHeatDriver[1];
        Runnable refresh=() -> {
            Air air=new Air(cabin(clock.now,warming),clock.now,-15.0,clock.now);
            model.update(clock.now,WALL+clock.now,levels,true,air);
            driver[0].apply(clock.now,levels,true,air);
        };
        driver[0]=new ModelHeatDriver(clock,model,refresh,message -> {});
        List<String> switches=new ArrayList<>();
        long[] firstPositive={-1};
        driver[0].start(seat,level -> {
            if(level>0 && firstPositive[0]<0) firstPositive[0]=clock.now;
            switches.add(String.format("[%.2f,%d]",clock.now/60000.0,level));
            levels.put(seat,level);
            refresh.run();
            return true;
        });
        List<SeatThermalModel.Parameters> profiles=SeatObserver.profilesWhileOn(seat);
        List<SeatThermalModel.State> physical=new ArrayList<>();
        for(SeatThermalModel.Parameters p:profiles) physical.add(new SeatThermalModel.State(-15,-15));
        double peak=-15;
        double error=0;
        for(long now=15000;now<=40*60000;now+=15000) {
            double e=SeatObserver.environment(cabin(now-7500,warming),-15.0);
            for(int i=0;i<physical.size();i++) {
                SeatThermalModel.State state=SeatThermalModel.advance(physical.get(i),levels.get(seat),e,profiles.get(i),0.25);
                physical.set(i,state);
                peak=Math.max(peak,state.surface);
            }
            clock.now=now;
            clock.tick();
            SeatObserver.Range range=model.estimate(seat).bank;
            for(SeatThermalModel.State state:physical) {
                error=Math.max(error,Math.max(range.low-state.surface,state.surface-range.high));
            }
        }
        double low=physical.stream().mapToDouble(s->s.surface).min().getAsDouble();
        double high=physical.stream().mapToDouble(s->s.surface).max().getAsDouble();
        System.out.printf("{\"seat\":\"%s\",\"unknown\":%s,\"warming\":%s,\"first_heat_minutes\":%.2f,\"final_low\":%.6f,\"final_high\":%.6f,\"peak\":%.6f,\"enclosure_error\":%.6f,\"switches\":[%s]}%n",
                seat,unknown,warming,firstPositive[0]/60000.0,low,high,peak,error,String.join(",",switches));
    }
}
