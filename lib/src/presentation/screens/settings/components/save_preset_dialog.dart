import 'package:autoheat/src/extensions/context_extensions.dart';
import 'package:autoheat/src/presentation/ui/custom_alert_dialog.dart';
import 'package:flutter/material.dart';

class SavePresetDialog extends StatefulWidget {
  const SavePresetDialog({super.key});

  @override
  State<SavePresetDialog> createState() => _SavePresetDialogState();
}

class _SavePresetDialogState extends State<SavePresetDialog> {
  final TextEditingController _nameController = TextEditingController();
  final FocusNode _focusNode = FocusNode();
  bool _isButtonEnabled = false;

  @override
  void initState() {
    super.initState();
    _nameController.addListener(_onTextChanged);

    WidgetsBinding.instance.addPostFrameCallback((_) {
      _focusNode.requestFocus();
    });
  }

  @override
  void dispose() {
    _nameController.removeListener(_onTextChanged);
    _nameController.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _onTextChanged() {
    setState(() {
      _isButtonEnabled = _nameController.text.trim().isNotEmpty;
    });
  }

  @override
  Widget build(BuildContext context) {
    return CustomAlertDialog(
      title: 'Сохранение пресета',
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: _nameController,
            focusNode: _focusNode,
            style: TextStyle(color: context.themeColors.textBody),
            decoration: InputDecoration(
              hintText: 'Название пресета',
              hintStyle: TextStyle(color: context.themeColors.textBody.withValues(alpha: 0.5)),
              filled: true,
              fillColor: context.themeColors.textBody.withValues(alpha: 0.1),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: BorderSide(
                  color: context.themeColors.primary.withValues(alpha: 0.3),
                ),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: BorderSide(
                  color: context.themeColors.primary.withValues(alpha: 0.3),
                ),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: BorderSide(color: context.themeColors.primary, width: 2),
              ),
            ),
          ),
        ],
      ),
      confirmText: 'Сохранить',
      isConfirmEnabled: _isButtonEnabled,
      onConfirm: () {
        Navigator.of(context).pop(_nameController.text.trim());
      },
    );
  }
}
