/*
 * Copyright (C) 2008 ZXing authors
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

package com.google.zxing.client.android;

import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.camera.CameraManager;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import com.google.zxing.FakeR;

import java.util.ArrayList;
import java.util.List;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

  private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192, 128, 64};
  private static final long ANIMATION_DELAY = 80L;
  private static final int CURRENT_POINT_OPACITY = 0xA0;
  private static final int MAX_RESULT_POINTS = 20;
  private static final int POINT_SIZE = 6;
  private static final String LOG_TAG = "ViewFinderView";
  
  private CameraManager cameraManager;
  private final Paint paint;
  private Bitmap resultBitmap;
  private final int maskColor;
  private final int resultColor;
  private final int laserColor;
  private final int resultPointColor;
  private int scannerAlpha;
  private List<ResultPoint> possibleResultPoints;
  private List<ResultPoint> lastPossibleResultPoints;
  private Boolean too_dark;

  private static FakeR fakeR;

  // This constructor is used when the class is built from an XML resource.
  public ViewfinderView(Context context, AttributeSet attrs) {
    super(context, attrs);

	fakeR = new FakeR(context);

    // Initialize these once for performance rather than calling them every time in onDraw().
	Log.i("VFV", "initialized");
    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Resources resources = getResources();
    maskColor = resources.getColor(fakeR.getId("color", "viewfinder_mask"));
    resultColor = resources.getColor(fakeR.getId("color", "result_view"));
    laserColor = resources.getColor(fakeR.getId("color", "viewfinder_laser"));
    resultPointColor = resources.getColor(fakeR.getId("color", "possible_result_points"));
    scannerAlpha = 0;
    possibleResultPoints = new ArrayList<ResultPoint>(5);
    lastPossibleResultPoints = null;
    too_dark = false;
  }

  public void setCameraManager(CameraManager cameraManager) {
    this.cameraManager = cameraManager;
  }

  @Override
  public void onDraw(Canvas canvas) {
	  Log.i("VFV","called: " + too_dark);
    
    Rect frame = cameraManager.getFramingRect();
    if (frame == null) {
      return;
    }
    int width = canvas.getWidth();
    int height = canvas.getHeight();
    if(too_dark){
    	  Log.i("VFV","drawing too dark");
    	  paint.setColor(Color.rgb(255,255,0));
          int middleX = (width) / 2;
          int middleY = (height) / 2;
          canvas.save();
          canvas.rotate(-90, middleX, middleY);
          paint.setTextAlign(Paint.Align.CENTER);
          paint.setTextSize(width/32);
    	  canvas.drawText("The image is too dark", middleX, middleY + frame.width() - 100, paint);
    	  canvas.restore();
      }
    if (cameraManager == null && !too_dark) {
        return; // not ready yet, early draw before done configuring
      }
    // Draw the exterior (i.e. outside the framing rect) darkened
    paint.setColor(resultBitmap != null ? resultColor : maskColor);
    canvas.drawRect(0, 0, width, frame.top, paint);
    canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
    canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
    canvas.drawRect(0, frame.bottom + 1, width, height, paint);
    Log.i("VFV","too_dark_in_VFV?: " + too_dark);
	if (resultBitmap != null) {
      // Draw the opaque result bitmap over the scanning rectangle
      paint.setAlpha(CURRENT_POINT_OPACITY);
      canvas.drawBitmap(resultBitmap, null, frame, paint); 
    } else {

    	// Draw a two pixel solid black border inside the framing rect
        paint.setColor(Color.rgb(51,188,255));
        canvas.drawRect(frame.left, frame.top, frame.right + 1, frame.top + 2, paint);
        canvas.drawRect(frame.left, frame.top + 2, frame.left + 2, frame.bottom - 1, paint);
        canvas.drawRect(frame.right - 1, frame.top, frame.right + 1, frame.bottom - 1, paint);
        canvas.drawRect(frame.left, frame.bottom - 1, frame.right + 1, frame.bottom + 1, paint);

        // Draw a red "laser scanner" line through the middle to show decoding is active
        paint.setColor(Color.rgb(51,188,255));
        int middleX = (width) / 2;
        int middleY = (height) / 2;
        canvas.save();
        canvas.rotate(-90, middleX, middleY);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(width/32);
        canvas.drawText("Match the square to the panel", middleX + 0, middleY - frame.width() + 60, paint);
        canvas.drawText("and wait a few seconds for the beep", middleX + 0, middleY - frame.width() + 120, paint);
        canvas.restore();
        Log.i(LOG_TAG, "line 130");
        Rect previewFrame = cameraManager.getFramingRectInPreview();
        Log.i(LOG_TAG, "line 132");
        float scaleX = frame.width() / (float) previewFrame.width();
        float scaleY = frame.height() / (float) previewFrame.height();

        List<ResultPoint> currentPossible = possibleResultPoints;
        List<ResultPoint> currentLast = lastPossibleResultPoints;
        if (currentPossible.isEmpty()) {
          lastPossibleResultPoints = null;
        } else {
          possibleResultPoints = new ArrayList<ResultPoint>(5);
          lastPossibleResultPoints = currentPossible;
          paint.setAlpha(CURRENT_POINT_OPACITY);
          paint.setColor(resultPointColor);
          synchronized (currentPossible) {
            for (ResultPoint point : currentPossible) {
              canvas.drawCircle(frame.left + (int) (point.getX() * scaleX),
                                frame.top + (int) (point.getY() * scaleY),
                                6.0f, paint);
            }
          }
        }
        if (currentLast != null) {
          paint.setAlpha(CURRENT_POINT_OPACITY / 2);
          paint.setColor(resultPointColor);
          synchronized (currentLast) {
            for (ResultPoint point : currentLast) {
              canvas.drawCircle(frame.left + (int) (point.getX() * scaleX),
                                frame.top + (int) (point.getY() * scaleY),
                                3.0f, paint);
            }
          }
        }

        // Request another update at the animation interval, but only repaint the laser line,
        // not the entire viewfinder mask.
        postInvalidateDelayed(ANIMATION_DELAY, frame.left, frame.top, frame.right, frame.bottom);
      }
    }

  public void drawViewfinder(Boolean tooDark) {
	Log.i("VFV", "drawViewFinderCalled: " + tooDark);
	too_dark = tooDark;
	
    Bitmap resultBitmap = this.resultBitmap;
    this.resultBitmap = null;
    if (resultBitmap != null) {
      resultBitmap.recycle();
    }
    invalidate();
  }

  /**
   * Draw a bitmap with the result points highlighted instead of the live scanning display.
   *
   * @param barcode An image of the decoded barcode.
   */
  public void drawResultBitmap(Bitmap barcode) {
    resultBitmap = barcode;
    invalidate();
  }
  
  public void addPossibleResultPoint(ResultPoint point) {
    List<ResultPoint> points = possibleResultPoints;
    synchronized (points) {
      points.add(point);
      int size = points.size();
      if (size > MAX_RESULT_POINTS) {
        // trim it
        points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
      }
    }
  }

}
