/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.folder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
public final class FolderNameProviderTest {
    private Context mContext;
    private WorkspaceItemInfo mItem1;
    private WorkspaceItemInfo mItem2;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mItem1 = new WorkspaceItemInfo(new AppInfo(
                new ComponentName("a.b.c", "a.b.c/a.b.c.d"),
                "title1",
                UserHandle.of(10),
                new Intent().setComponent(new ComponentName("a.b.c", "a.b.c/a.b.c.d"))
        ));
        mItem2 = new WorkspaceItemInfo(new AppInfo(
                new ComponentName("a.b.c", "a.b.c/a.b.c.d"),
                "title2",
                UserHandle.of(10),
                new Intent().setComponent(new ComponentName("a.b.c", "a.b.c/a.b.c.d"))
        ));
    }

    @Test
    public void getSuggestedFolderName_workAssignedToEnd() {
        ArrayList<WorkspaceItemInfo> list = new ArrayList<>();
        list.add(mItem1);
        list.add(mItem2);
        FolderNameInfos nameInfos = new FolderNameInfos();
        new FolderNameProvider().getSuggestedFolderName(mContext, list, nameInfos);
        assertEquals("Work", nameInfos.getLabels()[0]);

        nameInfos.setLabel(0, "candidate1", 1.0f);
        nameInfos.setLabel(1, "candidate2", 1.0f);
        nameInfos.setLabel(2, "candidate3", 1.0f);
        new FolderNameProvider().getSuggestedFolderName(mContext, list, nameInfos);
        assertEquals("Work", nameInfos.getLabels()[3]);
        assertTrue(nameInfos.hasSuggestions());
        assertTrue(nameInfos.hasPrimary());
    }
}
