/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.tapl;

import static com.android.launcher3.tapl.LauncherInstrumentation.WAIT_TIME_MS;

import android.graphics.Point;
import android.graphics.Rect;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.launcher3.tapl.LauncherInstrumentation.GestureScope;

import java.util.Collection;

/**
 * All widgets container.
 */
public final class Widgets extends LauncherInstrumentation.VisibleContainer {
    private static final int FLING_STEPS = 10;

    Widgets(LauncherInstrumentation launcher) {
        super(launcher);
        verifyActiveContainer();
    }

    /**
     * Flings forward (down) and waits the fling's end.
     */
    public void flingForward() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to fling forward in widgets")) {
            LauncherInstrumentation.log("Widgets.flingForward enter");
            final UiObject2 widgetsContainer = verifyActiveContainer();
            mLauncher.scroll(
                    widgetsContainer,
                    Direction.DOWN,
                    new Rect(0, 0, 0,
                            mLauncher.getBottomGestureMarginInContainer(widgetsContainer) + 1),
                    FLING_STEPS, false);
            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer("flung forward")) {
                verifyActiveContainer();
            }
            LauncherInstrumentation.log("Widgets.flingForward exit");
        }
    }

    /**
     * Flings backward (up) and waits the fling's end.
     */
    public void flingBackward() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to fling backwards in widgets")) {
            LauncherInstrumentation.log("Widgets.flingBackward enter");
            final UiObject2 widgetsContainer = verifyActiveContainer();
            mLauncher.scroll(
                    widgetsContainer,
                    Direction.UP,
                    new Rect(0, 0, mLauncher.getVisibleBounds(widgetsContainer).width(), 0),
                    FLING_STEPS, false);
            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer("flung back")) {
                verifyActiveContainer();
            }
            LauncherInstrumentation.log("Widgets.flingBackward exit");
        }
    }

    @Override
    protected LauncherInstrumentation.ContainerType getContainerType() {
        return LauncherInstrumentation.ContainerType.WIDGETS;
    }

    public Widget getWidget(String labelText) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "getting widget " + labelText + " in widgets list")) {
            final UiObject2 widgetsContainer = verifyActiveContainer();
            mLauncher.assertTrue("Widgets container didn't become scrollable",
                    widgetsContainer.wait(Until.scrollable(true), WAIT_TIME_MS));
            final Point displaySize = mLauncher.getRealDisplaySize();
            final BySelector labelSelector = By.clazz("android.widget.TextView").text(labelText);

            int i = 0;
            for (; ; ) {
                final Collection<UiObject2> cells = mLauncher.getObjectsInContainer(
                        widgetsContainer, "widgets_scroll_container");
                mLauncher.assertTrue("Widgets doesn't have 2 rows", cells.size() >= 2);
                for (UiObject2 cell : cells) {
                    final UiObject2 label = cell.findObject(labelSelector);
                    if (label == null) continue;

                    final UiObject2 widget = label.getParent().getParent();
                    mLauncher.assertEquals(
                            "View is not WidgetCell",
                            "com.android.launcher3.widget.WidgetCell",
                            widget.getClassName());

                    int maxWidth = 0;
                    for (UiObject2 sibling : widget.getParent().getChildren()) {
                        maxWidth = Math.max(mLauncher.getVisibleBounds(sibling).width(), maxWidth);
                    }

                    if (mLauncher.getVisibleBounds(widget).bottom
                            <= displaySize.y - mLauncher.getBottomGestureSize()) {
                        int visibleDelta = maxWidth - mLauncher.getVisibleBounds(widget).width();
                        if (visibleDelta > 0) {
                            Rect parentBounds = mLauncher.getVisibleBounds(cell);
                            mLauncher.linearGesture(parentBounds.centerX() + visibleDelta
                                            + mLauncher.getTouchSlop(),
                                    parentBounds.centerY(), parentBounds.centerX(),
                                    parentBounds.centerY(), 10, true, GestureScope.INSIDE);
                        }

                        return new Widget(mLauncher, widget);
                    }
                }

                mLauncher.assertTrue("Too many attempts", ++i <= 40);
                mLauncher.scrollToLastVisibleRow(widgetsContainer, cells, 0);
            }
        }
    }
}
