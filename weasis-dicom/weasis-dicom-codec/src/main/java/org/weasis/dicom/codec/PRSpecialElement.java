/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.data.PrDicomObject;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;

public class PRSpecialElement extends HiddenSpecialElement {

  public PRSpecialElement(DicomMediaIO mediaIO) {
    super(mediaIO);
  }

  @Override
  protected void initLabel() {

    Attributes dicom = ((DicomMediaIO) mediaIO).getDicomObject();
    /*
     * DICOM PS 3.3 - 2011 - CONTENT IDENTIFICATION MACRO. Used in Presentation State Identification C.11.10
     *
     * ContentLabel (mandatory): a label that is used to identify this SOP Instance.
     *
     * ContentDescription: a description of the content of the SOP Instance.
     */

    String clabel = dicom.getString(Tag.ContentLabel);
    if (clabel == null) {
      clabel = dicom.getString(Tag.ContentDescription);
    }

    if (clabel == null) {
      super.initLabel();
    } else {
      label = getLabelPrefix() + clabel;
    }
  }

  public PrDicomObject getPrDicomObject() {
    return (PrDicomObject) getTagValue(TagW.PrDicomObject);
  }

  public static List<PRSpecialElement> getPRSpecialElements(
      Collection<PRSpecialElement> specialElements, DicomImageElement img) {

    if (specialElements == null) {
      return Collections.emptyList();
    }
    List<PRSpecialElement> prList = null;

    for (PRSpecialElement prElement : specialElements) {
      if (PresentationStateReader.isImageApplicable(prElement, img)) {
        if (prList == null) {
          prList = new ArrayList<>();
        }
        prList.add(prElement);
      }
    }
    if (prList != null) {
      prList.sort(ORDER_BY_DATE);
    }
    return prList == null ? Collections.emptyList() : prList;
  }

  @Override
  public ResourceIconPath getIconPath() {
    return OtherIcon.PRESENTATION;
  }
}
