enum ThemeName {
  base,
  red,
  white;

  String get key => toString().split('.').last;

  static ThemeName fromKey(String key) {
    return ThemeName.values.firstWhere(
      (e) => e.key == key,
      orElse: () => ThemeName.white,
    );
  }
}
