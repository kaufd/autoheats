// FILE: lib/src/presentation/screens/presets/presets_tab.dart
// VERSION: 1.1.0
// START_MODULE_CONTRACT
//   PURPOSE: Merged Presets tab — Driver/Passenger toggle + editor + list + new-preset flow.
//   SCOPE: local state (selectedUser, editingPresetId, draftSettings, isNewPresetDraft),
//          wires PresetEditor + PresetList + UserSegmentToggle, delegates apply to parent,
//          name запрашивается через SavePresetDialog после нажатия Сохранить.
//   DEPENDS: M-UI-PRESETS, M-PRESET, M-MANUAL-SETTINGS, M-MODE, M-ENUMS, M-THEME
//   LINKS: M-UI-PRESETS, V-M-UI-PRESETS, DF-PRESET-APPLY, FA-001, FA-011
//   ROLE: RUNTIME
//   MAP_MODE: EXPORTS
// END_MODULE_CONTRACT
//
// START_MODULE_MAP
//   PresetsTab - StatefulWidget с onPresetApplied callback
//   _PresetsTabState.initState - load PresetCubit + ManualSettingsCubit
//   _PresetsTabState._buildEditor - resolve draft/active/default settings, wire PresetEditor
//   _PresetsTabState._buildList - filter by user, wire PresetList callbacks
//   _PresetsTabState._onUserChanged - reset editor/draft when toggling user
//   _PresetsTabState._onEdit - clone preset settings into draft, set editingPresetId
//   _PresetsTabState._onApply - save dirty edits if same preset, then onPresetApplied (E1)
//   _PresetsTabState._onDelete - delete preset, clear draft if was editing it
//   _PresetsTabState._onNewPreset - open empty/default draft (title = «Новый пресет»)
//   _PresetsTabState._onSave - update в edit-режиме или show SavePresetDialog для имени в new-режиме
//   _PresetsTabState._ensureDraftTarget - первое движение слайдера из idle поднимает edit/new режим
//   _PresetsTabState._handleActivePresetJump - case 3-α silent jump on selected preset change
// END_MODULE_MAP
//
// START_CHANGE_SUMMARY
//   LAST_CHANGE: [v1.1.0 - Имя нового пресета через SavePresetDialog после Save (вместо inline TextField)]
//   PREVIOUS_CHANGE: [v1.0.0 - Initial merged Presets tab]
// END_CHANGE_SUMMARY

import 'package:autoheat/src/app_enums.dart';
import 'package:autoheat/src/cubit/manual_settings_cubit.dart';
import 'package:autoheat/src/cubit/mode_cubit.dart';
import 'package:autoheat/src/cubit/preset_cubit.dart';
import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/models/manual_settings.dart';
import 'package:autoheat/src/models/preset.dart';
import 'package:autoheat/src/presentation/screens/presets/components/preset_editor.dart';
import 'package:autoheat/src/presentation/screens/presets/components/preset_list.dart';
import 'package:autoheat/src/presentation/screens/presets/components/user_segment_toggle.dart';
import 'package:autoheat/src/presentation/screens/settings/components/save_preset_dialog.dart';
import 'package:autoheat/src/presentation/ui/error_block.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class PresetsTab extends StatefulWidget {
  final void Function(Preset preset) onPresetApplied;

  const PresetsTab({super.key, required this.onPresetApplied});

  @override
  State<PresetsTab> createState() => _PresetsTabState();
}

class _PresetsTabState extends State<PresetsTab> {
  UserType _selectedUser = UserType.driver;
  String? _editingPresetId;
  ManualHeatSettings? _draftSettings;
  bool _isNewPresetDraft = false;

  @override
  void initState() {
    super.initState();
    context.read<ManualSettingsCubit>().initialize();
    context.read<PresetCubit>().loadAllPresets();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: 16),
          child: Center(
            child: UserSegmentToggle(
              selectedUser: _selectedUser,
              onChanged: _onUserChanged,
            ),
          ),
        ),
        Expanded(
          child: BlocConsumer<PresetCubit, PresetState>(
            listener: _handleActivePresetJump,
            builder: (context, presetState) {
              if (presetState.isLoading) {
                return Center(
                  child: CircularProgressIndicator(
                    color: context.themeColors.primary,
                  ),
                );
              }

              if (presetState.error != null) {
                return const ErrorBlock(
                    message: 'Ошибка загрузки пресетов');
              }

              // Внешний Expanded даёт bounded height; Row(crossAxisAlignment: stretch)
              // делит её поровну между колонками. IntrinsicHeight здесь не нужен и
              // ломал бы Spacer внутри редактора (intrinsic height колонки со Spacer = 0).
              return Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Expanded(child: _buildEditor(context, presetState)),
                  Container(
                    width: 2,
                    color:
                        context.themeColors.primary.withValues(alpha: 0.27),
                  ),
                  Expanded(child: _buildList(context, presetState)),
                ],
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildEditor(BuildContext context, PresetState presetState) {
    final activePreset = presetState.selectedPresets[_selectedUser];
    Preset? editingPreset;
    if (_editingPresetId != null) {
      for (final p in presetState.presets) {
        if (p.id == _editingPresetId) {
          editingPreset = p;
          break;
        }
      }
    }

    final fallbackSettings = editingPreset?.settings ??
        activePreset?.settings ??
        ManualHeatSettings.defaultFor(_selectedUser);

    final displayedSettings = _draftSettings ?? fallbackSettings;

    final displayedName = _isNewPresetDraft
        ? 'Новый пресет'
        : (editingPreset?.name ?? activePreset?.name);

    // Маркер «(активен)» отражает «показанный в редакторе пресет == активный».
    // Поэтому он остаётся при edit-режиме активного пресета, но скрывается, если
    // мы редактируем НЕактивный пресет или создаём новый.
    final shownPresetId = _isNewPresetDraft
        ? null
        : (editingPreset?.id ?? activePreset?.id);
    final isActiveShown =
        activePreset != null && shownPresetId == activePreset.id;

    // Save имеет цель только когда:
    //  - new-preset draft (имя спросим в диалоге), или
    //  - редактируется существующий пресет (editingPresetId).
    // В idle Save заблокирован; первое движение слайдера из idle через
    // _ensureDraftTarget переключит режим и Save станет активен.
    final isSaveEnabled = _isNewPresetDraft || _editingPresetId != null;

    return PresetEditor(
      userType: _selectedUser,
      settings: displayedSettings,
      presetName: displayedName,
      isActive: isActiveShown,
      isSaveEnabled: isSaveEnabled,
      onAutoHeatLevelChanged: _onAutoHeatLevelChanged,
      onTemperatureThresholdChanged: _onTemperatureThresholdChanged,
      onSave: () => _onSave(presetState),
    );
  }

  Widget _buildList(BuildContext context, PresetState presetState) {
    final activePresetId = presetState.selectedPresets[_selectedUser]?.id;
    return PresetList(
      presets: presetState.presets,
      selectedUser: _selectedUser,
      activePresetId: activePresetId,
      editingPresetId: _editingPresetId,
      onEdit: _onEdit,
      onApply: _onApply,
      onDelete: _onDelete,
      onNewPreset: _onNewPreset,
    );
  }

  void _onUserChanged(UserType user) {
    setState(() {
      _selectedUser = user;
      _editingPresetId = null;
      _draftSettings = null;
      _isNewPresetDraft = false;
    });
  }

  void _onEdit(Preset preset) {
    setState(() {
      _editingPresetId = preset.id;
      _draftSettings = preset.settings;
      _isNewPresetDraft = false;
    });
  }

  Future<void> _onApply(Preset preset) async {
    if (_editingPresetId == preset.id && _draftSettings != null) {
      await context
          .read<PresetCubit>()
          .updatePresetSettings(preset, _draftSettings!);
    }
    widget.onPresetApplied(preset);
  }

  Future<void> _onDelete(Preset preset) async {
    await context.read<PresetCubit>().deletePreset(preset.id, preset.userType);
    if (!mounted) return;
    if (_editingPresetId == preset.id) {
      setState(() {
        _editingPresetId = null;
        _draftSettings = null;
      });
    }
  }

  void _onNewPreset() {
    setState(() {
      _editingPresetId = null;
      _draftSettings = ManualHeatSettings.defaultFor(_selectedUser);
      _isNewPresetDraft = true;
    });
  }

  Future<void> _onSave(PresetState presetState) async {
    final draft = _draftSettings;
    if (draft == null) return;

    if (_isNewPresetDraft) {
      final name = await showDialog<String>(
        context: context,
        builder: (_) => const SavePresetDialog(),
      );
      if (name == null || name.trim().isEmpty) return;
      if (!mounted) return;

      final modeCubit = context.read<ModeCubit>();
      final heatMode = HeatModeExtension.fromString(
          modeCubit.getModeByUser(_selectedUser));
      final heatLevel = modeCubit.getHeatLevelByUser(_selectedUser);

      await context.read<PresetCubit>().savePreset(
            name: name.trim(),
            userType: _selectedUser,
            settings: draft,
            heatMode: heatMode,
            heatLevel: heatLevel,
          );

      if (!mounted) return;
      setState(() {
        _isNewPresetDraft = false;
        _draftSettings = null;
        _editingPresetId = null;
      });
      return;
    }

    final id = _editingPresetId;
    if (id == null) return;
    Preset? preset;
    for (final p in presetState.presets) {
      if (p.id == id) {
        preset = p;
        break;
      }
    }
    preset ??= presetState.selectedPresets[_selectedUser];
    if (preset == null) return;

    await context.read<PresetCubit>().updatePresetSettings(preset, draft);
    if (!mounted) return;
    setState(() {
      _draftSettings = null;
    });
  }

  void _onAutoHeatLevelChanged(AutoHeatLevel autoHeatLevel, int duration) {
    setState(() {
      final state = context.read<PresetCubit>().state;
      _ensureDraftTarget(state);
      final source = _draftSettings ?? _currentEditorSettings(state);
      _draftSettings = source.copyWith(
        autoHeatLevels: source.autoHeatLevels
            .map((level) => level == autoHeatLevel
                ? level.copyWith(duration: duration)
                : level)
            .toList(),
      );
    });
  }

  void _onTemperatureThresholdChanged(double threshold) {
    setState(() {
      final state = context.read<PresetCubit>().state;
      _ensureDraftTarget(state);
      final source = _draftSettings ?? _currentEditorSettings(state);
      _draftSettings = source.copyWith(temperatureThreshold: threshold);
    });
  }

  // Первое изменение slider'а из idle должно зафиксировать «цель сохранения»:
  // если есть активный пресет — переходим в его edit-режим; если нет — в new-preset
  // draft (имя спросим в диалоге при Save).
  void _ensureDraftTarget(PresetState state) {
    if (_isNewPresetDraft || _editingPresetId != null) return;
    final active = state.selectedPresets[_selectedUser];
    if (active != null) {
      _editingPresetId = active.id;
      return;
    }
    _isNewPresetDraft = true;
  }

  ManualHeatSettings _currentEditorSettings(PresetState presetState) {
    if (_editingPresetId != null) {
      for (final p in presetState.presets) {
        if (p.id == _editingPresetId) {
          return p.settings;
        }
      }
    }
    final active = presetState.selectedPresets[_selectedUser];
    return active?.settings ?? ManualHeatSettings.defaultFor(_selectedUser);
  }

  void _handleActivePresetJump(BuildContext context, PresetState state) {
    final newActive = state.selectedPresets[_selectedUser];
    if (newActive == null) return;

    if (_isNewPresetDraft) return; // user is mid-create, don't blow it away

    if (_editingPresetId != null && _editingPresetId != newActive.id) {
      // case 3-α: silent jump
      setState(() {
        _editingPresetId = null;
        _draftSettings = null;
      });
    }
  }
}
