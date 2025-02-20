enum ThemeType {
  base,
  red,
  white;

  String get key => toString().split('.').last;

  static ThemeType fromKey(String key) {
    return ThemeType.values.firstWhere(
      (e) => e.key == key,
      orElse: () => ThemeType.base,
    );
  }
}
