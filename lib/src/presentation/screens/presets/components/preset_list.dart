import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/config/color_constants.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/presentation/ui/custom_alert_dialog.dart';
import 'package:flutter/material.dart';

class PresetList extends StatelessWidget {
  final List<Preset> presets;
  final Preset? selectedPreset;
  final Function(Preset) onPresetSelected;
  final Function(Preset) onPresetDeleted;

  const PresetList({
    super.key,
    required this.presets,
    required this.onPresetSelected,
    required this.onPresetDeleted,
    this.selectedPreset,
  });

  @override
  Widget build(BuildContext context) {
    if (presets.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.bookmark_outline,
              size: 48,
              color: context.themeColors.textMuted,
            ),
            const SizedBox(height: 16),
            Text(
              'Нет сохраненных пресетов',
              style: context.textStyle.textNav.copyWith(
                color: context.themeColors.textMuted,
                fontWeight: FontWeight.w400,
              ),
            ),
          ],
        ),
      );
    }

    final driverPresets = presets.where((p) => p.userType == UserType.driver).toList();
    final passengerPresets = presets.where((p) => p.userType == UserType.passenger).toList();

    return SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildPresetSection(context, 'Водитель', driverPresets),
            const SizedBox(width: 16),
            _buildPresetSection(context, 'Пассажир', passengerPresets),
          ],
        ),
      ),
    );
  }

  Widget _buildPresetSection(BuildContext context, String title, List<Preset> presets) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Center(
              child: Text(
                title,
                style: context.textStyle.paragraph3.copyWith(fontWeight: FontWeight.w600),
              ),
            ),
          ),
          ...presets.map((preset) => _buildPresetItem(context, preset)),
        ],
      ),
    );
  }

  Widget _buildPresetItem(BuildContext context, Preset preset) {
    final isSelected = selectedPreset?.id == preset.id;

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      child: Material(
        color: isSelected
            ? context.themeColors.primary.withValues(alpha: 0.2)
            : Colors.white.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: () => onPresetSelected(preset),
          child: Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color:
                    isSelected ? context.themeColors.primary : Colors.white.withValues(alpha: 0.1),
                width: isSelected ? 2 : 1,
              ),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        preset.name,
                        style: context.textStyle.paragraph1,
                      ),
                      const SizedBox(height: 8),
                      RichText(
                        text: TextSpan(
                          children: [
                            TextSpan(
                              text: 'Создан: ',
                              style: context.textStyle.paragraph3
                                  .copyWith(color: context.themeColors.primary),
                            ),
                            TextSpan(
                              text: _formatDate(preset.createdAt),
                              style: context.textStyle.paragraph3,
                            ),
                          ],
                        ),
                      ),
                      // if (preset.lastUsed != null) ...[
                      //   const SizedBox(height: 2),
                      //   RichText(
                      //     text: TextSpan(
                      //       children: [
                      //         TextSpan(
                      //           text: 'Использован: ',
                      //           style: context.textStyle.paragraph2
                      //               .copyWith(color: context.themeColors.primary),
                      //         ),
                      //         TextSpan(
                      //           text: _formatDate(preset.lastUsed!),
                      //           style: context.textStyle.paragraph2,
                      //         ),
                      //       ],
                      //     ),
                      //   ),
                      // ],
                    ],
                  ),
                ),
                IconButton(
                  onPressed: () => _showDeleteDialog(context, preset),
                  icon: Icon(
                    Icons.delete_outline,
                    color: ColorConstants.error,
                  ),
                  tooltip: 'Удалить пресет',
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  String _formatDate(DateTime date) {
    final differenceInDays = DateTime.now().difference(date).inDays;

    return switch (differenceInDays) {
      0 => 'сегодня',
      1 => 'вчера',
      < 7 => '$differenceInDays дн. назад',
      _ => '${date.day}.${date.month}.${date.year}',
    };
  }

  void _showDeleteDialog(BuildContext context, Preset preset) {
    showDialog(
      context: context,
      builder: (context) => CustomAlertDialog(
        content: Padding(
          padding: EdgeInsets.only(bottom: 32),
          child: Text(
            'Удалить пресет "${preset.name}"?',
            textAlign: TextAlign.center,
            style: context.textStyle.paragraph1,
          ),
        ),
        confirmText: 'Удалить',
        onConfirm: () {
          Navigator.of(context).pop();
          onPresetDeleted(preset);
        },
      ),
    );
  }
}
