import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/config/color_constants.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/presentation/ui/custom_alert_dialog.dart';
import 'package:flutter/material.dart';

class PresetList extends StatelessWidget {
  final List<Preset> presets;
  final UserType selectedUser;
  final String? activePresetId;
  final String? editingPresetId;
  final ValueChanged<Preset> onEdit;
  final ValueChanged<Preset> onApply;
  final ValueChanged<Preset> onDelete;
  final VoidCallback onNewPreset;

  const PresetList({
    super.key,
    required this.presets,
    required this.selectedUser,
    required this.activePresetId,
    required this.editingPresetId,
    required this.onEdit,
    required this.onApply,
    required this.onDelete,
    required this.onNewPreset,
  });

  @override
  Widget build(BuildContext context) {
    final userPresets =
        presets.where((p) => p.userType == selectedUser).toList();

    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: userPresets.isEmpty
                ? _buildEmptyState(context)
                : ListView.separated(
                    itemCount: userPresets.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 8),
                    itemBuilder: (_, index) =>
                        _buildRow(context, userPresets[index]),
                  ),
          ),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerRight,
            child: _buildNewPresetButton(context),
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState(BuildContext context) {
    return Center(
      child: Text(
        'Пока нет сохранённых пресетов',
        style: context.textStyle.paragraph2.copyWith(
          color: context.themeColors.textMuted,
        ),
      ),
    );
  }

  Widget _buildRow(BuildContext context, Preset preset) {
    final isActive = preset.id == activePresetId;
    final isEditing = preset.id == editingPresetId;

    return Material(
      color: isEditing
          ? context.themeColors.primary.withValues(alpha: 0.18)
          : ColorConstants.systemWhite.withValues(alpha: 0.05),
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: isActive
                ? context.themeColors.primary
                : ColorConstants.systemWhite.withValues(alpha: 0.1),
            width: isActive ? 2 : 1,
          ),
        ),
        child: Row(
          children: [
            if (isActive)
              Padding(
                padding: const EdgeInsets.only(right: 6),
                child: Icon(Icons.star,
                    size: 18, color: context.themeColors.primary),
              ),
            Expanded(
              child: Text(
                preset.name,
                style: context.textStyle.paragraph1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            IconButton(
              onPressed: () => onEdit(preset),
              icon: Icon(Icons.edit, color: context.themeColors.primary),
              tooltip: 'Редактировать',
              iconSize: 22,
              constraints: const BoxConstraints(minWidth: 44, minHeight: 44),
            ),
            IconButton(
              onPressed: () => onApply(preset),
              icon: Icon(Icons.play_arrow, color: context.themeColors.primary),
              tooltip: 'Применить',
              iconSize: 24,
              constraints: const BoxConstraints(minWidth: 44, minHeight: 44),
            ),
            IconButton(
              onPressed: () => _confirmDelete(context, preset),
              icon: Icon(Icons.delete_outline, color: ColorConstants.error),
              tooltip: 'Удалить',
              iconSize: 22,
              constraints: const BoxConstraints(minWidth: 44, minHeight: 44),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNewPresetButton(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: onNewPreset,
      icon: const Icon(Icons.add),
      label: const Text('Новый пресет'),
      style: OutlinedButton.styleFrom(
        foregroundColor: context.themeColors.primary,
        side: BorderSide(color: context.themeColors.primary, width: 1),
      ),
    );
  }

  void _confirmDelete(BuildContext context, Preset preset) {
    showDialog(
      context: context,
      builder: (_) => CustomAlertDialog(
        content: Padding(
          padding: const EdgeInsets.only(bottom: 32),
          child: Text(
            'Удалить пресет "${preset.name}"?',
            textAlign: TextAlign.center,
            style: context.textStyle.paragraph1,
          ),
        ),
        confirmText: 'Удалить',
        onConfirm: () {
          Navigator.of(context).pop();
          onDelete(preset);
        },
      ),
    );
  }
}
