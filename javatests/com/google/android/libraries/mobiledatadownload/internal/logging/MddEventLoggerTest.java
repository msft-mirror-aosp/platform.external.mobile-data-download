/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.libraries.mobiledatadownload.internal.logging;

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.libraries.mobiledatadownload.Logger;
import com.google.android.libraries.mobiledatadownload.internal.logging.EventLogger.FileGroupStatusWithDetails;
import com.google.android.libraries.mobiledatadownload.testing.FakeTimeSource;
import com.google.android.libraries.mobiledatadownload.testing.TestFlags;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.mobiledatadownload.LogEnumsProto.MddClientEvent;
import com.google.mobiledatadownload.LogEnumsProto.MddFileGroupDownloadStatus;
import com.google.mobiledatadownload.LogProto.AndroidClientInfo;
import com.google.mobiledatadownload.LogProto.DataDownloadFileGroupStats;
import com.google.mobiledatadownload.LogProto.MddDeviceInfo;
import com.google.mobiledatadownload.LogProto.MddFileGroupStatus;
import com.google.mobiledatadownload.LogProto.MddLogData;
import com.google.mobiledatadownload.LogProto.StableSamplingInfo;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

import java.security.SecureRandom;
import java.util.Random;

@RunWith(RobolectricTestRunner.class)
public class MddEventLoggerTest {

    @Rule
    public final MockitoRule mocks = MockitoJUnit.rule();

    private static final int SOME_MODULE_VERSION = 42;
    private static final int SAMPLING_ALWAYS = 1;
    private static final int SAMPLING_NEVER = 0;

    @Mock
    private Logger mockLogger;
    private MddEventLogger mddEventLogger;

    private final Context context = ApplicationProvider.getApplicationContext();
    private final TestFlags flags = new TestFlags();

    @Before
    public void setUp() throws Exception {
        SharedPreferences loggingStateSharedPrefs =
                context.getSharedPreferences("loggingStateSharedPrefs", 0);
        mddEventLogger =
                new MddEventLogger(
                        context,
                        mockLogger,
                        SOME_MODULE_VERSION,
                        new LogSampler(flags, new SecureRandom()),
                        flags);
        mddEventLogger.setLoggingStateStore(
                SharedPreferencesLoggingState.create(
                        () -> loggingStateSharedPrefs, new FakeTimeSource(), directExecutor(),
                        new Random(0)));
    }

    private MddLogData.Builder newLogDataBuilderWithClientInfo() {
        return MddLogData.newBuilder()
                .setAndroidClientInfo(
                        AndroidClientInfo.newBuilder()
                                .setModuleVersion(SOME_MODULE_VERSION)
                                .setHostPackageName(context.getPackageName()));
    }

    @Test
    public void testSampleInterval_zero_none() {
        assertFalse(LogUtil.shouldSampleInterval(0));
    }

    @Test
    public void testSampleInterval_negative_none() {
        assertFalse(LogUtil.shouldSampleInterval(-1));
    }

    @Test
    public void testSampleInterval_always() {
        assertTrue(LogUtil.shouldSampleInterval(1));
    }

    @Test
    public void testLogMddEvents_noLog() throws Exception {
        overrideDefaultSampleInterval(SAMPLING_NEVER);

        DataDownloadFileGroupStats fileGroupStats =
                DataDownloadFileGroupStats.newBuilder()
                        .setFileGroupName("fileGroup")
                        .setFileGroupVersionNumber(1)
                        .setBuildId(123)
                        .setVariantId("testVariant")
                        .build();
        MddFileGroupStatus fileGroupStatus =
                MddFileGroupStatus.newBuilder()
                        .setFileGroupDownloadStatus(MddFileGroupDownloadStatus.Code.COMPLETE)
                        .build();
        FileGroupStatusWithDetails fileGroupStatusWithDetails =
                FileGroupStatusWithDetails.create(fileGroupStatus, fileGroupStats);

        mddEventLogger
                .logMddFileGroupStats(
                        () -> immediateFuture(ImmutableList.of(fileGroupStatusWithDetails)))
                .get();

        verifyNoInteractions(mockLogger);
    }

    @Test
    public void testLogMddEvents() throws Exception {
        overrideDefaultSampleInterval(SAMPLING_ALWAYS);

        DataDownloadFileGroupStats fileGroupStats =
                DataDownloadFileGroupStats.newBuilder()
                        .setFileGroupName("fileGroup")
                        .setFileGroupVersionNumber(1)
                        .setBuildId(123)
                        .setVariantId("testVariant")
                        .build();
        MddFileGroupStatus fileGroupStatus =
                MddFileGroupStatus.newBuilder()
                        .setFileGroupDownloadStatus(MddFileGroupDownloadStatus.Code.COMPLETE)
                        .build();
        FileGroupStatusWithDetails fileGroupStatusWithDetails =
                FileGroupStatusWithDetails.create(fileGroupStatus, fileGroupStats);

        MddLogData expectedData =
                newLogDataBuilderWithClientInfo()
                        .setSamplingInterval(SAMPLING_ALWAYS)
                        .setDataDownloadFileGroupStats(fileGroupStats)
                        .setMddFileGroupStatus(fileGroupStatus)
                        .setDeviceInfo(MddDeviceInfo.newBuilder().setDeviceStorageLow(false))
                        .setStableSamplingInfo(getStableSamplingInfo())
                        .build();

        mddEventLogger
                .logMddFileGroupStats(
                        () -> immediateFuture(ImmutableList.of(fileGroupStatusWithDetails)))
                .get();

        verify(mockLogger)
                .log(eq(expectedData),
                        eq(MddClientEvent.Code.DATA_DOWNLOAD_FILE_GROUP_STATUS_VALUE));
    }

    private void overrideDefaultSampleInterval(int sampleInterval) {
        flags.mddDefaultSampleInterval = Optional.of(sampleInterval);
        flags.groupStatsLoggingSampleInterval = Optional.of(sampleInterval);
    }

    private StableSamplingInfo getStableSamplingInfo() {
        if (flags.enableRngBasedDeviceStableSampling()) {
            return StableSamplingInfo.newBuilder()
                    .setStableSamplingUsed(true)
                    .setStableSamplingFirstEnabledTimestampMs(0)
                    .setPartOfAlwaysLoggingGroup(false)
                    .setInvalidSamplingRateUsed(false)
                    .build();
        }

        return StableSamplingInfo.newBuilder().setStableSamplingUsed(false).build();
    }
}