/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.rs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import javax.swing.JOptionPane;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.auth.AuthProvider;
import org.weasis.core.api.auth.AuthRegistration;
import org.weasis.core.api.auth.DefaultAuthMethod;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.StringUtil.Suffix;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.PluginOpeningStrategy;
import org.weasis.dicom.explorer.pref.node.AuthenticationPersistence;
import org.weasis.dicom.explorer.wado.DownloadManager;
import org.weasis.dicom.explorer.wado.DownloadManager.PriorityTaskComparator;
import org.weasis.dicom.explorer.wado.LoadSeries;
import org.weasis.dicom.mf.WadoParameters;
import org.weasis.dicom.web.InvokeImageDisplay;

public class RsQueryParams extends ExplorerTask<Boolean, String> {
  private static final Logger LOGGER = LoggerFactory.getLogger(RsQueryParams.class);

  public static final String P_DICOMWEB_URL = "dicomweb.url";
  public static final String P_QUERY_EXT = "query.ext";
  public static final String P_RETRIEVE_EXT = "retrieve.ext";
  public static final String P_SHOW_WHOLE_STUDY = "show.whole.study";
  public static final String P_ACCEPT_EXT = "accept.ext";
  public static final String P_AUTH_UID = "auth.uid";
  public static final String P_OIDC_ISSUER = "oidc.issuer";
  public static final String P_OIDC_USER = "oidc.user";
  public static final String P_PAGE_EXT = "page.size";

  private final DicomModel dicomModel;
  private final Map<String, LoadSeries> seriesMap;
  private final Properties properties;
  private final Map<String, String> queryHeaders;
  private final Map<String, String> retrieveHeaders;
  protected final Map<String, List<String>> requestMap;
  protected final RsQueryResult arcConfig;

  protected final PluginOpeningStrategy openingStrategy;

  public RsQueryParams(
      DicomModel dicomModel,
      Properties properties,
      Map<String, List<String>> requestMap,
      Map<String, String> queryHeaders,
      Map<String, String> retrieveHeaders) {
    super(Messages.getString("DicomExplorer.loading"), true);
    this.dicomModel = Objects.requireNonNull(dicomModel);
    this.properties = Objects.requireNonNull(properties);
    this.requestMap = Objects.requireNonNull(requestMap);
    String url = properties.getProperty(P_DICOMWEB_URL);
    if (StringUtil.hasText(url)) {
      if (url.endsWith("/")) {
        properties.setProperty(P_DICOMWEB_URL, url.substring(0, url.length() - 1));
      }
    } else {
      throw new IllegalArgumentException("DICOMWeb URL cannot be null");
    }

    this.seriesMap = new HashMap<>();
    this.queryHeaders = queryHeaders == null ? Collections.emptyMap() : queryHeaders;
    this.retrieveHeaders = retrieveHeaders == null ? Collections.emptyMap() : retrieveHeaders;
    String uid = properties.getProperty(RsQueryParams.P_AUTH_UID);
    AuthMethod method = null;
    if (StringUtil.hasText(uid)) {
      method = AuthenticationPersistence.getAuthMethod(uid);
    }
    if (method == null) {
      String issuer = properties.getProperty(RsQueryParams.P_OIDC_ISSUER);
      if (StringUtil.hasText(issuer)) {
        if (issuer.endsWith("/")) {
          issuer = issuer.substring(0, issuer.length() - 1);
        }
        AuthProvider p =
            new AuthProvider(
                "OIDC",
                issuer + "/protocol/openid-connect/auth", // NON-NLS
                issuer + "/protocol/openid-connect/token", // NON-NLS
                issuer + "/protocol/openid-connect/revoke", // NON-NLS
                true);
        AuthRegistration r = new AuthRegistration(null, null, "openid"); // NON-NLS
        r.setUser(properties.getProperty(RsQueryParams.P_OIDC_USER));
        method = new DefaultAuthMethod(UUID.randomUUID().toString(), p, r);
      }
    }
    this.arcConfig = new RsQueryResult(this, method);
    this.openingStrategy = new PluginOpeningStrategy(DownloadManager.getOpeningViewer());
  }

  public static Map<String, String> getHeaders(List<String> urlHeaders) {
    Map<String, String> headers = new HashMap<>();
    for (String h : LangUtil.emptyIfNull(urlHeaders)) {
      String[] val = h.split(":", 2);
      if (val.length == 1) {
        headers.put(val[0].trim().toLowerCase(), "");
      } else if (val.length == 2) {
        String name = val[0].trim().toLowerCase();
        String value = val[1].trim();
        // Hack for dcm4chee-arc integration
        if ("authorization".equals(name) // NON-NLS
            && value.length() > 14
            && value.startsWith("&access_token=")) { // NON-NLS
          value = "Bearer " + value.substring(14); // NON-NLS
        }
        headers.put(name, value);
      }
    }
    return headers;
  }

  public static Map<String, List<String>> getQueryMap(String query) {
    String[] params = query.split("&");
    Map<String, List<String>> map = new HashMap<>();
    for (String param : params) {
      String[] val = param.split("=", 2);
      String name = val[0];
      if (!name.isEmpty()) {
        List<String> v = map.get(name);
        if (v == null) {
          v = new ArrayList<>();
          map.put(val[0], v);
        }
        if (val.length == 1) {
          v.add("");
        } else if (val.length == 2) {
          v.add(val[1]);
        }
      }
    }
    return map;
  }

  @Override
  protected void done() {
    openingStrategy.reset();
  }

  @Override
  protected Boolean doInBackground() throws Exception {
    fillPatientList();

    if (!seriesMap.isEmpty()) {
      openingStrategy.prepareImport();
      WadoParameters wp = new WadoParameters("", true, true);
      getRetrieveHeaders().forEach(wp::addHttpTag);
      wp.addHttpTag("Accept", "image/jpeg"); // NON-NLS

      for (final LoadSeries loadSeries : seriesMap.values()) {
        String modality = TagD.getTagValue(loadSeries.getDicomSeries(), Tag.Modality, String.class);
        boolean ps = ("PR".equals(modality) || "KO".equals(modality)); // NON-NLS
        if (!ps) {
          loadSeries.startDownloadImageReference(wp);
        }
        loadSeries.setPOpeningStrategy(openingStrategy);
        DownloadManager.addLoadSeries(loadSeries, dicomModel, loadSeries.isStartDownloading());
      }

      // Sort tasks from the download priority order (low number has a higher priority), TASKS
      // is sorted from low to high priority.
      DownloadManager.getTasks().sort(Collections.reverseOrder(new PriorityTaskComparator()));

      DownloadManager.CONCURRENT_EXECUTOR.prestartAllCoreThreads();
    }
    return true;
  }

  private void fillPatientList() {
    try {
      String requestType = getRequestType();

      if (InvokeImageDisplay.STUDY_LEVEL.equals(requestType)) {
        String stuID = getReqStudyUID();
        String anbID = getReqAccessionNumber();
        if (StringUtil.hasText(anbID)) {
          arcConfig.buildFromStudyAccessionNumber(Collections.singletonList(anbID));
        } else if (StringUtil.hasText(stuID)) {
          arcConfig.buildFromStudyInstanceUID(Collections.singletonList(stuID));

        } else {
          LOGGER.error("Not ID found for STUDY request type: {}", requestType);
          showErrorMessage(
              Messages.getString("RsQueryParams.missing_study_uid"),
              Messages.getString("RsQueryParams.no_sudy_uid"));
        }
      } else if (InvokeImageDisplay.PATIENT_LEVEL.equals(requestType)) {
        String patID = getReqPatientID();
        if (StringUtil.hasText(patID)) {
          arcConfig.buildFromPatientID(Collections.singletonList(patID));
        }
      } else if (requestType != null) {
        LOGGER.error("Not supported IID request type: {}", requestType);
        showErrorMessage(
            Messages.getString("RsQueryParams.unexpect_req"),
            Messages.getString("RsQueryParams.idd_type")
                + StringUtil.COLON_AND_SPACE
                + requestType);
      } else {
        arcConfig.buildFromSopInstanceUID(getReqObjectUIDs());
        arcConfig.buildFromSeriesInstanceUID(getReqSeriesUIDs());
        arcConfig.buildFromStudyAccessionNumber(getReqAccessionNumbers());
        arcConfig.buildFromStudyInstanceUID(getReqStudyUIDs());
        arcConfig.buildFromPatientID(getReqPatientIDs());
      }
    } catch (Exception e) {
      LOGGER.error("Error when building the patient list", e);
      showErrorMessage(
          Messages.getString("RsQueryParams.unexpect_error"),
          Messages.getString("RsQueryParams.error_build_mf")
              + StringUtil.COLON
              + "\n"
              + StringUtil.getTruncatedString(e.getMessage(), 130, Suffix.THREE_PTS));
    }
  }

  private static void showErrorMessage(String title, String msg) {
    GuiExecutor.execute(
        () ->
            JOptionPane.showMessageDialog(
                GuiUtils.getUICore().getBaseArea(), msg, title, JOptionPane.ERROR_MESSAGE));
  }

  private static String getFirstParam(List<String> list) {
    if (list != null && !list.isEmpty()) {
      return list.get(0);
    }
    return null;
  }

  public boolean isAcceptNoImage() {
    return LangUtil.getEmptytoFalse(properties.getProperty("accept.noimage"));
  }

  public DicomModel getDicomModel() {
    return dicomModel;
  }

  public Map<String, LoadSeries> getSeriesMap() {
    return seriesMap;
  }

  public Properties getProperties() {
    return properties;
  }

  public Map<String, String> getQueryHeaders() {
    return queryHeaders;
  }

  public Map<String, String> getRetrieveHeaders() {
    return retrieveHeaders;
  }

  public String getBaseUrl() {
    return properties.getProperty(P_DICOMWEB_URL);
  }

  public boolean hasPatients() {
    return !arcConfig.getPatients().isEmpty();
  }

  public void clearAllPatients() {
    arcConfig.getPatients().clear();
  }

  public void removePatientId(List<String> patientIdList, boolean containsIssuer) {
    arcConfig.removePatientId(patientIdList, containsIssuer);
  }

  public void removeStudyUid(List<String> studyUidList) {
    arcConfig.removeStudyUid(studyUidList);
  }

  public void removeAccessionNumber(List<String> accessionNumberList) {
    arcConfig.removeAccessionNumber(accessionNumberList);
  }

  public void removeSeriesUid(List<String> seriesUidList) {
    arcConfig.removeSeriesUid(seriesUidList);
  }

  public String getRequestType() {
    return getFirstParam(requestMap.get(InvokeImageDisplay.REQUEST_TYPE));
  }

  public String getReqPatientID() {
    return getFirstParam(requestMap.get(InvokeImageDisplay.PATIENT_ID));
  }

  public String getPatientName() {
    return getFirstParam(requestMap.get(InvokeImageDisplay.PATIENT_NAME));
  }

  public String getPatientBirthDate() {
    return getFirstParam(requestMap.get(InvokeImageDisplay.PATIENT_BIRTHDATE));
  }

  public String getLowerDateTime() {
    return getFirstParam(requestMap.get(InvokeImageDisplay.LOWER_DATETIME));
  }

  public String getUpperDateTime() {
    return getFirstParam(requestMap.get(InvokeImageDisplay.UPPER_DATETIME));
  }

  public String getMostRecentResults() {
    return getFirstParam(requestMap.get(InvokeImageDisplay.MOST_RECENT_RESULTS));
  }

  public String getKeywords() {
    return getFirstParam(requestMap.get(InvokeImageDisplay.KEYWORDS));
  }

  public String getModalitiesInStudy() {
    return getFirstParam(requestMap.get(InvokeImageDisplay.MODALITIES_IN_STUDY));
  }

  public String getReqStudyUID() {
    return getFirstParam(requestMap.get(InvokeImageDisplay.STUDY_UID));
  }

  public String getReqAccessionNumber() {
    return getFirstParam(requestMap.get(InvokeImageDisplay.ACCESSION_NUMBER));
  }

  public String getReqSeriesUID() {
    return getFirstParam(requestMap.get(InvokeImageDisplay.SERIES_UID));
  }

  public String getReqObjectUID() {
    return getFirstParam(requestMap.get(InvokeImageDisplay.OBJECT_UID));
  }

  public List<String> getReqPatientIDs() {
    return requestMap.get(InvokeImageDisplay.PATIENT_ID);
  }

  public List<String> getReqStudyUIDs() {
    return requestMap.get(InvokeImageDisplay.STUDY_UID);
  }

  public List<String> getReqAccessionNumbers() {
    return requestMap.get(InvokeImageDisplay.ACCESSION_NUMBER);
  }

  public List<String> getReqSeriesUIDs() {
    return requestMap.get(InvokeImageDisplay.SERIES_UID);
  }

  public List<String> getReqObjectUIDs() {
    return requestMap.get(InvokeImageDisplay.OBJECT_UID);
  }
}
