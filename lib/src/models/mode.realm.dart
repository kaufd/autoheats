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
  ) {
    RealmObjectBase.set(this, 'userName', userName);
    RealmObjectBase.set(this, 'modeName', modeName);
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
    };
  }

  static EJsonValue _toEJson(Mode value) => value.toEJson();
  static Mode _fromEJson(EJsonValue ejson) {
    if (ejson is! Map<String, dynamic>) return raiseInvalidEJson(ejson);
    return switch (ejson) {
      {
        'userName': EJsonValue userName,
        'modeName': EJsonValue modeName,
      } =>
        Mode(
          fromEJson(userName),
          fromEJson(modeName),
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
    ]);
  }();

  @override
  SchemaObject get objectSchema => RealmObjectBase.getSchema(this) ?? schema;
}
