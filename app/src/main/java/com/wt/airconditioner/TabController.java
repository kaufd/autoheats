package com.wt.airconditioner;

import android.view.View;

/** Контракт вкладки: получить свою страницу, перекраситься, узнать о показе. */
interface TabController {

    /** Страница создана адаптером: разовая привязка View и слушателей. */
    void bind(View page);

    /** Сменилась тема — или страница только что создана и красится впервые. */
    void applyTheme(ThemePalette palette);

    /**
     * Приходит всем вкладкам на каждое переключение, а не одной показанной:
     * логу нужны оба края — он копит текст молча, пока не виден.
     */
    void onTabVisible(boolean visible);
}
