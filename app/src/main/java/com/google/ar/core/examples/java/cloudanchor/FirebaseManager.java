/*
 * Copyright 2019 Google LLC
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

package com.google.ar.core.examples.java.cloudanchor;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

/** A helper class to manage all communications with Firebase. */
class FirebaseManager {
  private static final String TAG =
      CloudAnchorActivity.class.getSimpleName() + "." + FirebaseManager.class.getSimpleName();

  /** Listener for a new room code. */
  interface RoomCodeListener {

    /** Invoked when a new room code is available from Firebase. */
    void onNewRoomCode(Context currentContext, Long newRoomCode);

    /** Invoked if a Firebase Database Error happened while fetching the room code. */
    void onError(DatabaseError error);
  }

  /** Listener for a new cloud anchor ID. */
  interface CloudAnchorIdListener {

    /** Invoked when a new cloud anchor ID is available. */
    void onNewCloudAnchorId(String cloudAnchorId);
  }

  /** Listener for last room Idx */
  interface CloudAnchorIdsListener {
    /** Invoked when a new cloud anchor ID is available. */
    void onCloudAnchorIds(ArrayList<Pair<Long, String>> anchorIds);
  }

  // Names of the nodes used in the Firebase Database
  private static final String ROOT_FIREBASE_HOTSPOTS = "hotspot_list";
  private static final String ROOT_LAST_ROOM_CODE = "last_room_code";
  private static final String ROOM_LAST_IDX = "last_idx_code";

  // Some common keys and values used when writing to the Firebase Database.
  private static final String KEY_DISPLAY_NAME = "display_name";
  private static final String KEY_ANCHOR_ID = "hosted_anchor_id";
  private static final String KEY_TIMESTAMP = "updated_at_timestamp";
  private static final String DISPLAY_NAME_VALUE = "Android EAP Sample";

  private final FirebaseApp app;
  private final DatabaseReference hotspotListRef;
  private final DatabaseReference roomCodeRef;
  private final DatabaseReference roomIdxRef;
  private Context cloudAnchorManagerContext;

  private DatabaseReference currentRoomRef = null;
  private ValueEventListener currentRoomListener = null;
  private DatabaseReference currentRoomIdxRef = null;
  private ValueEventListener currentRoomIdxListener = null;


  /**
   * Default constructor for the FirebaseManager.
   *
   * @param context The application context.
   */
  FirebaseManager(Context context) {
    app = FirebaseApp.initializeApp(context);
    cloudAnchorManagerContext = context;
    if (app != null) {
      String url = "https://indoor-ar-navigation-5eade-default-rtdb.firebaseio.com/";
      DatabaseReference rootRef = FirebaseDatabase.getInstance().getReferenceFromUrl(url);
      hotspotListRef = rootRef.child(ROOT_FIREBASE_HOTSPOTS);
      roomCodeRef = rootRef.child(ROOT_LAST_ROOM_CODE);
      roomIdxRef = rootRef.child(ROOT_LAST_ROOM_CODE).child(ROOM_LAST_IDX);

      DatabaseReference.goOnline();
    } else {
      Log.d(TAG, "Could not connect to Firebase Database!");
      hotspotListRef = null;
      roomCodeRef = null;
      roomIdxRef = null;
    }
  }

  /**
   * Gets a new room code from the Firebase Database. Invokes the listener method when a new room
   * code is available.
   */
  void getNewRoomCode(RoomCodeListener listener) {
    Preconditions.checkNotNull(app, "Firebase App was null");
    roomCodeRef.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            Long nextCode = Long.valueOf(1);
            Object currVal = currentData.getValue();
            if (currVal != null) {
              Long lastCode = Long.valueOf(currVal.toString());
              nextCode = lastCode + 1;
            }
            currentData.setValue(nextCode);
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            if (!committed) {
              listener.onError(error);
              return;
            }
            Long roomCode = currentData.getValue(Long.class);
            listener.onNewRoomCode(cloudAnchorManagerContext, roomCode);
          }
        });
  }

  /** Stores the given anchor ID in the given room code. */
  void storeAnchorIdInRoom(Long roomCode, Long roomIdx, String cloudAnchorId, String cloudAnchorName) {
    Preconditions.checkNotNull(app, "Firebase App was null");
    DatabaseReference roomRef = hotspotListRef.child(String.valueOf(roomCode));
    DatabaseReference roomIdxRef = hotspotListRef.child(String.valueOf(roomCode)).child(String.valueOf(roomIdx));
    roomRef.child(ROOM_LAST_IDX).setValue(roomIdx);
    roomIdxRef.child(KEY_DISPLAY_NAME).setValue(cloudAnchorName);
    roomIdxRef.child(KEY_ANCHOR_ID).setValue(cloudAnchorId);
    roomIdxRef.child(KEY_TIMESTAMP).setValue(System.currentTimeMillis());
  }

  /**
   * Registers a new listener for the given room code. The listener is invoked whenever the data for
   * the room code is changed.
   */
  void registerNewListenerForRoom(Long roomCode, Long roomIdx, CloudAnchorIdListener listener) {
    Preconditions.checkNotNull(app, "Firebase App was null");
    clearRoomListener();
    currentRoomRef = hotspotListRef.child(String.valueOf(roomCode)).child(String.valueOf(roomIdx));
    currentRoomListener =
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot dataSnapshot) {
            Object valObj = dataSnapshot.child(KEY_ANCHOR_ID).getValue();
            if (valObj != null) {
              String anchorId = String.valueOf(valObj);
              if (!anchorId.isEmpty()) {
                listener.onNewCloudAnchorId(anchorId);
              }
            }
          }

          @Override
          public void onCancelled(DatabaseError databaseError) {
            Log.w(TAG, "The Firebase operation was cancelled.", databaseError.toException());
          }
        };
    currentRoomRef.addValueEventListener(currentRoomListener);
  }

  void registerNewListenerForRoom(Long roomCode, CloudAnchorIdsListener listener) {
    Preconditions.checkNotNull(app, "Firebase App was null");
    clearRoomListener();
    currentRoomRef = hotspotListRef.child(String.valueOf(roomCode));
    currentRoomListener =
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot dataSnapshot) {
                Object valObj = dataSnapshot.child(ROOM_LAST_IDX).getValue();
                if (valObj != null) {
                  Long lastIdx = Long.parseLong(String.valueOf(valObj));
                  ArrayList<Pair<Long, String>> anchorIds = new ArrayList<>();
                  for (long i = 0; i <= lastIdx; i++) {
                    Object anchorIdObj = dataSnapshot.child(String.valueOf(i)).child(KEY_ANCHOR_ID).getValue();
                    if (anchorIdObj != null) {
                      String anchorId = String.valueOf(anchorIdObj);
                      anchorIds.add(new Pair<>(i, anchorId));
                    }
                  }
                  listener.onCloudAnchorIds(anchorIds);
                }
              }

              @Override
              public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "The Firebase operation was cancelled.", databaseError.toException());
              }
            };
    currentRoomRef.addValueEventListener(currentRoomListener);
  }

  void clearRoomListener() {
    if (currentRoomListener != null && currentRoomRef != null) {
      currentRoomRef.removeEventListener(currentRoomListener);
      currentRoomListener = null;
      currentRoomRef = null;
    }
  }
}
