// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mode.dart';

// **************************************************************************
// RealmObjectGenerator
// **************************************************************************

// ignore_for_file: type=lint
class Mode extends _Mode with RealmEntity, RealmObjectBase, RealmObject {
  Mode(
    String userName,
    String modeName,
    int heatLevel,
  ) {
    RealmObjectBase.set(this, 'userName', userName);
    RealmObjectBase.set(this, 'modeName', modeName);
    RealmObjectBase.set(this, 'heatLevel', heatLevel);
  }

  Mode._();

  @override
  String get userName =>
      RealmObjectBase.get<String>(this, 'userName') as String;
  @override
  set userName(String value) => RealmObjectBase.set(this, 'userName', value);

  @override
  String get modeName =>
      RealmObjectBase.get<String>(this, 'modeName') as String;
  @override
  set modeName(String value) => RealmObjectBase.set(this, 'modeName', value);

  @override
  int get heatLevel => RealmObjectBase.get<int>(this, 'heatLevel') as int;
  @override
  set heatLevel(int value) => RealmObjectBase.set(this, 'heatLevel', value);

  @override
  Stream<RealmObjectChanges<Mode>> get changes =>
      RealmObjectBase.getChanges<Mode>(this);

  @override
  Stream<RealmObjectChanges<Mode>> changesFor([List<String>? keyPaths]) =>
      RealmObjectBase.getChangesFor<Mode>(this, keyPaths);

  @override
  Mode freeze() => RealmObjectBase.freezeObject<Mode>(this);

  EJsonValue toEJson() {
    return <String, dynamic>{
      'userName': userName.toEJson(),
      'modeName': modeName.toEJson(),
      'heatLevel': heatLevel.toEJson(),
    };
  }

  static EJsonValue _toEJson(Mode value) => value.toEJson();
  static Mode _fromEJson(EJsonValue ejson) {
    if (ejson is! Map<String, dynamic>) return raiseInvalidEJson(ejson);
    return switch (ejson) {
      {
        'userName': EJsonValue userName,
        'modeName': EJsonValue modeName,
        'heatLevel': EJsonValue heatLevel,
      } =>
        Mode(
          fromEJson(userName),
          fromEJson(modeName),
          fromEJson(heatLevel),
        ),
      _ => raiseInvalidEJson(ejson),
    };
  }

  static final schema = () {
    RealmObjectBase.registerFactory(Mode._);
    register(_toEJson, _fromEJson);
    return const SchemaObject(ObjectType.realmObject, Mode, 'Mode', [
      SchemaProperty('userName', RealmPropertyType.string),
      SchemaProperty('modeName', RealmPropertyType.string),
      SchemaProperty('heatLevel', RealmPropertyType.int),
    ]);
  }();

  @override
  SchemaObject get objectSchema => RealmObjectBase.getSchema(this) ?? schema;
}
