import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';

class AppTextStyle {
  final BuildContext _context;

  AppTextStyle(this._context);

  TextStyle get heading1 => TextStyle(
        fontSize: 24,
        fontStyle: FontStyle.normal,
        fontWeight: FontWeight.w700,
        color: _context.themeColors.textBody,
      );

  TextStyle get textnav => TextStyle(
        fontSize: 18,
        fontStyle: FontStyle.normal,
        fontWeight: FontWeight.w400,
        letterSpacing: 0,
        color: _context.themeColors.textButtonText,
      );

  TextStyle get textnavActive => TextStyle(
        fontSize: 18,
        fontStyle: FontStyle.normal,
        fontWeight: FontWeight.w700,
        letterSpacing: 0,
        color: _context.themeColors.textButtonText,
      );

  TextStyle get paragraph1 => TextStyle(
        fontSize: 16,
        fontStyle: FontStyle.normal,
        fontWeight: FontWeight.w400,
        color: _context.themeColors.textBody,
      );
}
