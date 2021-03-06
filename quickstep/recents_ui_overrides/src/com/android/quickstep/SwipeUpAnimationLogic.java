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
package com.android.quickstep;

import static com.android.launcher3.anim.Interpolators.ACCEL_1_5;
import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.views.FloatingIconView;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.util.TransformParams.BuilderProxy;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams.Builder;

public abstract class SwipeUpAnimationLogic {

    protected static final Rect TEMP_RECT = new Rect();
    private static final Interpolator PULLBACK_INTERPOLATOR = DEACCEL;

    protected DeviceProfile mDp;

    protected final Context mContext;
    protected final RecentsAnimationDeviceState mDeviceState;
    protected final GestureState mGestureState;
    protected final TaskViewSimulator mTaskViewSimulator;

    protected final TransformParams mTransformParams;

    // Shift in the range of [0, 1].
    // 0 => preview snapShot is completely visible, and hotseat is completely translated down
    // 1 => preview snapShot is completely aligned with the recents view and hotseat is completely
    // visible.
    protected final AnimatedFloat mCurrentShift = new AnimatedFloat(this::updateFinalShift);

    // The distance needed to drag to reach the task size in recents.
    protected int mTransitionDragLength;
    // How much further we can drag past recents, as a factor of mTransitionDragLength.
    protected float mDragLengthFactor = 1;
    // Start resisting when swiping past this factor of mTransitionDragLength.
    private float mDragLengthFactorStartPullback = 1f;
    // This is how far down we can scale down, where 0f is full screen and 1f is recents.
    private float mDragLengthFactorMaxPullback = 1f;

    protected AnimatorPlaybackController mWindowTransitionController;

    public SwipeUpAnimationLogic(Context context, RecentsAnimationDeviceState deviceState,
            GestureState gestureState, TransformParams transformParams) {
        mContext = context;
        mDeviceState = deviceState;
        mGestureState = gestureState;
        mTaskViewSimulator = new TaskViewSimulator(context, gestureState.getActivityInterface());
        mTransformParams = transformParams;

        mTaskViewSimulator.setLayoutRotation(
                mDeviceState.getCurrentActiveRotation(), mDeviceState.getDisplayRotation());
    }

    protected void initTransitionEndpoints(DeviceProfile dp) {
        mDp = dp;

        mTaskViewSimulator.setDp(dp);
        mTransitionDragLength = mGestureState.getActivityInterface().getSwipeUpDestinationAndLength(
                dp, mContext, TEMP_RECT,
                mTaskViewSimulator.getOrientationState().getOrientationHandler());

        if (mDeviceState.isFullyGesturalNavMode()) {
            // We can drag all the way to the top of the screen.
            mDragLengthFactor = (float) dp.heightPx / mTransitionDragLength;

            float startScale = mTaskViewSimulator.getFullScreenScale();
            // Start pulling back when RecentsView scale is 0.75f, and let it go down to 0.5f.
            mDragLengthFactorStartPullback = (0.75f - startScale) / (1 - startScale);
            mDragLengthFactorMaxPullback = (0.5f - startScale) / (1 - startScale);
        } else {
            mDragLengthFactor = 1;
            mDragLengthFactorStartPullback = mDragLengthFactorMaxPullback = 1;
        }

        PendingAnimation pa = new PendingAnimation(mTransitionDragLength * 2);
        mTaskViewSimulator.addAppToOverviewAnim(pa, t -> t * mDragLengthFactor);
        mWindowTransitionController = pa.createPlaybackController();
    }

    @UiThread
    public void updateDisplacement(float displacement) {
        // We are moving in the negative x/y direction
        displacement = -displacement;
        float shift;
        if (displacement > mTransitionDragLength * mDragLengthFactor && mTransitionDragLength > 0) {
            shift = mDragLengthFactor;
        } else {
            float translation = Math.max(displacement, 0);
            shift = mTransitionDragLength == 0 ? 0 : translation / mTransitionDragLength;
            if (shift > mDragLengthFactorStartPullback) {
                float pullbackProgress = Utilities.getProgress(shift,
                        mDragLengthFactorStartPullback, mDragLengthFactor);
                pullbackProgress = PULLBACK_INTERPOLATOR.getInterpolation(pullbackProgress);
                shift = mDragLengthFactorStartPullback + pullbackProgress
                        * (mDragLengthFactorMaxPullback - mDragLengthFactorStartPullback);
            }
        }

        mCurrentShift.updateValue(shift);
    }

    /**
     * Called when the value of {@link #mCurrentShift} changes
     */
    @UiThread
    public abstract void updateFinalShift();

    protected PagedOrientationHandler getOrientationHandler() {
        return mTaskViewSimulator.getOrientationState().getOrientationHandler();
    }

    protected abstract class HomeAnimationFactory {

        public FloatingIconView mIconView;

        public HomeAnimationFactory(@Nullable FloatingIconView iconView) {
            mIconView = iconView;
        }

        public @NonNull RectF getWindowTargetRect() {
            PagedOrientationHandler orientationHandler = getOrientationHandler();
            DeviceProfile dp = mDp;
            final int halfIconSize = dp.iconSizePx / 2;
            float primaryDimension = orientationHandler
                    .getPrimaryValue(dp.availableWidthPx, dp.availableHeightPx);
            float secondaryDimension = orientationHandler
                    .getSecondaryValue(dp.availableWidthPx, dp.availableHeightPx);
            final float targetX =  primaryDimension / 2f;
            final float targetY = secondaryDimension - dp.hotseatBarSizePx;
            // Fallback to animate to center of screen.
            return new RectF(targetX - halfIconSize, targetY - halfIconSize,
                    targetX + halfIconSize, targetY + halfIconSize);
        }

        public abstract @NonNull AnimatorPlaybackController createActivityAnimationToHome();

        public void playAtomicAnimation(float velocity) {
            // No-op
        }
    }

    /**
     * Creates an animation that transforms the current app window into the home app.
     * @param startProgress The progress of {@link #mCurrentShift} to start the window from.
     * @param homeAnimationFactory The home animation factory.
     */
    protected RectFSpringAnim createWindowAnimationToHome(float startProgress,
            HomeAnimationFactory homeAnimationFactory) {
        final RectF targetRect = homeAnimationFactory.getWindowTargetRect();
        final FloatingIconView fiv = homeAnimationFactory.mIconView;
        final boolean isFloatingIconView = fiv != null;

        mWindowTransitionController.setPlayFraction(startProgress / mDragLengthFactor);
        mTaskViewSimulator.apply(mTransformParams.setProgress(startProgress));
        RectF cropRectF = new RectF(mTaskViewSimulator.getCurrentCropRect());

        // Matrix to map a rect in Launcher space to window space
        Matrix homeToWindowPositionMap = new Matrix();
        mTaskViewSimulator.applyWindowToHomeRotation(homeToWindowPositionMap);

        final RectF startRect = new RectF(cropRectF);
        mTaskViewSimulator.getCurrentMatrix().mapRect(startRect);
        // Move the startRect to Launcher space as floatingIconView runs in Launcher
        Matrix windowToHomePositionMap = new Matrix();
        homeToWindowPositionMap.invert(windowToHomePositionMap);
        windowToHomePositionMap.mapRect(startRect);

        RectFSpringAnim anim = new RectFSpringAnim(startRect, targetRect, mContext);
        if (isFloatingIconView) {
            anim.addAnimatorListener(fiv);
            fiv.setOnTargetChangeListener(anim::onTargetPositionChanged);
            fiv.setFastFinishRunnable(anim::end);
        }

        SpringAnimationRunner runner = new SpringAnimationRunner(
                homeAnimationFactory, cropRectF, homeToWindowPositionMap);
        anim.addOnUpdateListener(runner);
        anim.addAnimatorListener(runner);
        return anim;
    }

    /**
     * @param progress The progress of the animation to the home screen.
     * @return The current alpha to set on the animating app window.
     */
    protected float getWindowAlpha(float progress) {
        // Alpha interpolates between [1, 0] between progress values [start, end]
        final float start = 0f;
        final float end = 0.85f;

        if (progress <= start) {
            return 1f;
        }
        if (progress >= end) {
            return 0f;
        }
        return Utilities.mapToRange(progress, start, end, 1, 0, ACCEL_1_5);
    }

    protected class SpringAnimationRunner extends AnimationSuccessListener
            implements RectFSpringAnim.OnUpdateListener, BuilderProxy {

        final Rect mCropRect = new Rect();
        final Matrix mMatrix = new Matrix();

        final RectF mWindowCurrentRect = new RectF();
        final Matrix mHomeToWindowPositionMap;

        final FloatingIconView mFIV;
        final AnimatorPlaybackController mHomeAnim;
        final RectF mCropRectF;

        final float mStartRadius;
        final float mEndRadius;
        final float mWindowAlphaThreshold;

        SpringAnimationRunner(HomeAnimationFactory factory, RectF cropRectF,
                Matrix homeToWindowPositionMap) {
            mHomeAnim = factory.createActivityAnimationToHome();
            mCropRectF = cropRectF;
            mHomeToWindowPositionMap = homeToWindowPositionMap;

            cropRectF.roundOut(mCropRect);
            mFIV = factory.mIconView;

            // End on a "round-enough" radius so that the shape reveal doesn't have to do too much
            // rounding at the end of the animation.
            mStartRadius = mTaskViewSimulator.getCurrentCornerRadius();
            mEndRadius = cropRectF.width() / 2f;

            // We want the window alpha to be 0 once this threshold is met, so that the
            // FolderIconView can be seen morphing into the icon shape.
            mWindowAlphaThreshold = mFIV != null ? 1f - SHAPE_PROGRESS_DURATION : 1f;
        }

        @Override
        public void onUpdate(RectF currentRect, float progress) {
            mHomeAnim.setPlayFraction(progress);
            mHomeToWindowPositionMap.mapRect(mWindowCurrentRect, currentRect);

            mMatrix.setRectToRect(mCropRectF, mWindowCurrentRect, ScaleToFit.FILL);
            float cornerRadius = Utilities.mapRange(progress, mStartRadius, mEndRadius);
            mTransformParams
                    .setTargetAlpha(getWindowAlpha(progress))
                    .setCornerRadius(cornerRadius);

            mTransformParams.applySurfaceParams(mTransformParams.createSurfaceParams(this));
            if (mFIV != null) {
                mFIV.update(currentRect, 1f, progress,
                        mWindowAlphaThreshold, mMatrix.mapRadius(cornerRadius), false);
            }
        }

        @Override
        public void onBuildTargetParams(
                Builder builder, RemoteAnimationTargetCompat app, TransformParams params) {
            builder.withMatrix(mMatrix)
                    .withWindowCrop(mCropRect)
                    .withCornerRadius(params.getCornerRadius());
        }

        @Override
        public void onCancel() {
            if (mFIV != null) {
                mFIV.fastFinish();
            }
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mHomeAnim.dispatchOnStart();
        }

        @Override
        public void onAnimationSuccess(Animator animator) {
            mHomeAnim.getAnimationPlayer().end();
        }
    }

    public interface RunningWindowAnim {
        void end();

        void cancel();

        static RunningWindowAnim wrap(Animator animator) {
            return new RunningWindowAnim() {
                @Override
                public void end() {
                    animator.end();
                }

                @Override
                public void cancel() {
                    animator.cancel();
                }
            };
        }

        static RunningWindowAnim wrap(RectFSpringAnim rectFSpringAnim) {
            return new RunningWindowAnim() {
                @Override
                public void end() {
                    rectFSpringAnim.end();
                }

                @Override
                public void cancel() {
                    rectFSpringAnim.cancel();
                }
            };
        }
    }
}
