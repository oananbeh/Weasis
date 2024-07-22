/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.macro;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.util.DicomUtils;

public class SOPInstanceReference extends Module {

  public SOPInstanceReference(Attributes dcmItems) {
    super(dcmItems);
  }

  public SOPInstanceReference() {
    super(new Attributes());
  }

  public static Collection<SOPInstanceReference> toSOPInstanceReferenceMacros(Sequence seq) {
    if (seq == null || seq.isEmpty()) {
      return Collections.emptyList();
    }

    ArrayList<SOPInstanceReference> list = new ArrayList<>(seq.size());

    for (Attributes attr : seq) {
      list.add(new SOPInstanceReference(attr));
    }

    return list;
  }

  public int[] getReferencedFrameNumber() {
    return DicomUtils.getIntArrayFromDicomElement(dcmItems, Tag.ReferencedFrameNumber, null);
  }

  /**
   * Add frame number references (1 to n). Note: no frame means the entire series.
   *
   * @param dicomFrameNumber the list of frame number
   */
  public void setReferencedFrameNumber(int... dicomFrameNumber) {
    dcmItems.setInt(Tag.ReferencedFrameNumber, VR.IS, dicomFrameNumber);
  }

  public String getReferencedSOPInstanceUID() {
    return dcmItems.getString(Tag.ReferencedSOPInstanceUID);
  }

  public void setReferencedSOPInstanceUID(String uid) {
    dcmItems.setString(Tag.ReferencedSOPInstanceUID, VR.UI, uid);
  }

  public String getReferencedSOPClassUID() {
    return dcmItems.getString(Tag.ReferencedSOPClassUID);
  }

  public void setReferencedSOPClassUID(String uid) {
    dcmItems.setString(Tag.ReferencedSOPClassUID, VR.UI, uid);
  }
}
