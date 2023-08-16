/*
 * Copyright (C) 2023 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.pso.beam.contentextract.clients;

import java.util.Arrays;

/** */
public enum GoogleDriveAPIMimeTypes {
  AUDIO("application/vnd.google-apps.audio"),
  DOCUMENT("application/vnd.google-apps.document"),
  THIRDPARTY_SHORTCUT("application/vnd.google-apps.drive-sdk"),
  DRAWING("application/vnd.google-apps.drawing"),
  FILE("application/vnd.google-apps.file"),
  FOLDER("application/vnd.google-apps.folder"),
  FORM("application/vnd.google-apps.form"),
  FUSION_TABLE("application/vnd.google-apps.fusiontable"),
  JAMBOARD("application/vnd.google-apps.jam"),
  MAP("application/vnd.google-apps.map"),
  PHOTO("application/vnd.google-apps.photo"),
  PRESENTATION("application/vnd.google-apps.presentation"),
  APP_SCRIPT("application/vnd.google-apps.script"),
  SHORTCUT("application/vnd.google-apps.shortcut"),
  SITE("application/vnd.google-apps.site"),
  SPREADSHEET("application/vnd.google-apps.spreadsheet"),
  UNKNOWN("application/vnd.google-apps.unknown"),
  VIDEO("application/vnd.google-apps.video");

  public static final String MIME_TYPE_KEY = "mime-type";

  public static GoogleDriveAPIMimeTypes get(String value) {
    return Arrays.stream(GoogleDriveAPIMimeTypes.values())
        .filter(val -> val.mimeType().equals(value))
        .findAny()
        .orElseThrow(() -> new RuntimeException("MimeType value not recognized: " + value));
  }

  private final String mimeType;

  GoogleDriveAPIMimeTypes(String mimeType) {
    this.mimeType = mimeType;
  }

  public String mimeType() {
    return mimeType;
  }
}
