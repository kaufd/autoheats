import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';

class AppTextStyle {
  final BuildContext _context;

  AppTextStyle(this._context);

  TextStyle get heading1 => TextStyle(
        fontSize: 28,
        fontStyle: FontStyle.normal,
        fontWeight: FontWeight.w700,
      );

  TextStyle get heading2 => TextStyle(
        fontSize: 24,
        fontStyle: FontStyle.normal,
        fontWeight: FontWeight.w700,
        color: _context.themeColors.textBody,
      );

  TextStyle get heading3 => TextStyle(
        fontSize: 18,
        fontStyle: FontStyle.normal,
        fontWeight: FontWeight.w600,
        color: _context.themeColors.textBody,
      );

  TextStyle get textNav => TextStyle(
        fontSize: 21,
        fontStyle: FontStyle.normal,
        fontWeight: FontWeight.w500,
        color: _context.themeColors.textButtonPrimary,
      );

  TextStyle get textNavActive => TextStyle(
        fontSize: 21,
        fontStyle: FontStyle.normal,
        fontWeight: FontWeight.w700,
        color: _context.themeColors.textButtonSelected,
      );

  TextStyle get textSegmentedButton => TextStyle(
        fontSize: 21,
        fontStyle: FontStyle.normal,
        fontWeight: FontWeight.w400,
      );

  TextStyle get paragraph1 => TextStyle(
        fontSize: 16,
        fontStyle: FontStyle.normal,
        fontWeight: FontWeight.w400,
        color: _context.themeColors.textBody,
      );

  TextStyle get paragraph2 => TextStyle(
        fontSize: 14,
        fontStyle: FontStyle.normal,
        fontWeight: FontWeight.w400,
        color: _context.themeColors.textBody,
      );

  TextStyle get paragraph3 => TextStyle(
        fontSize: 12,
        fontStyle: FontStyle.normal,
        fontWeight: FontWeight.w400,
        color: _context.themeColors.textBody,
      );

  TextStyle get textSettings => TextStyle(
        fontSize: 24,
        fontStyle: FontStyle.normal,
        fontWeight: FontWeight.w400,
        color: _context.themeColors.textBody,
      );
}
