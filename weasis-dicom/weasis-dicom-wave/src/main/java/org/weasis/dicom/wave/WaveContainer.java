/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.FileIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.SeriesViewerUI;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.pref.LauncherToolBar;
import org.weasis.core.ui.util.ForcedAcceptPrintService;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.DicomExportAction;
import org.weasis.dicom.explorer.DicomFieldsView;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomViewerPlugin;
import org.weasis.dicom.explorer.ExportToolBar;
import org.weasis.dicom.explorer.ImportToolBar;
import org.weasis.dicom.wave.dockable.MeasureAnnotationTool;

public class WaveContainer extends DicomViewerPlugin implements PropertyChangeListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(WaveContainer.class);

  public static final GridBagLayoutModel DEFAULT_VIEW =
      new GridBagLayoutModel(
          "1x1", // NON-NLS
          "1x1", // NON-NLS
          1,
          1,
          WaveView.class.getName()); // NON-NLS

  public static final List<GridBagLayoutModel> LAYOUT_LIST = List.of(DEFAULT_VIEW);

  public static final List<SynchView> SYNCH_LIST = List.of(SynchView.NONE);

  public static final SeriesViewerUI UI = new SeriesViewerUI(WaveContainer.class);
  static final ImageViewerEventManager<DicomImageElement> ECG_EVENT_MANAGER =
      new ImageViewerEventManager<>() {

        @Override
        public boolean updateComponentsListener(ViewCanvas<DicomImageElement> defaultView2d) {
          // Do nothing
          return true;
        }

        @Override
        public void resetDisplay() {
          // Do nothing
        }

        @Override
        public void setSelectedView2dContainer(
            ImageViewerPlugin<DicomImageElement> selectedView2dContainer) {
          this.selectedView2dContainer = selectedView2dContainer;
        }

        @Override
        public void keyTyped(KeyEvent e) {
          // Do nothing
        }

        @Override
        public void keyPressed(KeyEvent e) {
          // Do nothing
        }

        @Override
        public void keyReleased(KeyEvent e) {
          // Do nothing
        }

        @Override
        public String resolvePlaceholders(String template) {
          return DicomExportAction.resolvePlaceholders(template, this);
        }
      };
  protected WaveView ecgView;

  public WaveContainer() {
    this(DEFAULT_VIEW, null);
  }

  public WaveContainer(GridBagLayoutModel layoutModel, String uid) {
    super(
        ECG_EVENT_MANAGER,
        layoutModel,
        uid,
        WaveFactory.NAME,
        ResourceUtil.getIcon(FileIcon.ECG),
        null);
    setSynchView(SynchView.NONE);

    if (!UI.init.getAndSet(true)) {
      List<Toolbar> toolBars = UI.toolBars;
      // Add standard toolbars
      final BundleContext context = AppProperties.getBundleContext(this.getClass());
      if (context == null) {
        LOGGER.error("Cannot get BundleContext");
        return;
      }
      String bundleName = context.getBundle().getSymbolicName();
      String componentName = InsertableUtil.getCName(this.getClass());
      String key = "enable"; // NON-NLS
      WProperties preferences = GuiUtils.getUICore().getSystemPreferences();

      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(ImportToolBar.class),
          key,
          true)) {
        Optional<Toolbar> b =
            GuiUtils.getUICore().getExplorerPluginToolbars().stream()
                .filter(ImportToolBar.class::isInstance)
                .findFirst();
        b.ifPresent(toolBars::add);
      }
      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(ExportToolBar.class),
          key,
          true)) {
        Optional<Toolbar> b =
            GuiUtils.getUICore().getExplorerPluginToolbars().stream()
                .filter(ExportToolBar.class::isInstance)
                .findFirst();
        b.ifPresent(toolBars::add);
      }
      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(WaveformToolBar.class),
          key,
          true)) {
        toolBars.add(new WaveformToolBar(20));
      }
      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(LauncherToolBar.class),
          key,
          true)) {
        toolBars.add(new LauncherToolBar(getEventManager(), 130));
      }

      PluginTool tool;
      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(MeasureTool.class),
          key,
          true)) {
        tool = new MeasureAnnotationTool();
        eventManager.addSeriesViewerListener((SeriesViewerListener) tool);
        UI.tools.add(tool);
      }
    }
  }

  @Override
  public void setSelectedImagePaneFromFocus(ViewCanvas<DicomImageElement> defaultView2d) {
    setSelectedImagePane(defaultView2d);
  }

  @Override
  public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
    if (menuRoot != null) {
      menuRoot.removeAll();
      menuRoot.setText(WaveFactory.NAME);

      List<Action> actions = getPrintActions();
      if (actions != null) {
        JMenu printMenu = new JMenu(Messages.getString("ECGontainer.print"));
        for (Action action : actions) {
          JMenuItem item = new JMenuItem(action);
          printMenu.add(item);
        }
        menuRoot.add(printMenu);
      }
    }
    return menuRoot;
  }

  @Override
  public SeriesViewerUI getSeriesViewerUI() {
    return UI;
  }

  @Override
  public void setSelected(boolean selected) {
    super.setSelected(true);
    if (selected) {
      if (ecgView != null
          && !UI.tools.isEmpty()
          && UI.tools.getFirst() instanceof MeasureAnnotationTool tool) {
        ecgView.setAnnotationTool(tool);
        tool.setSeries(ecgView.getSeries());
        ecgView.updateMarkersTable();
      }
    }
  }

  @Override
  public void close() {
    super.close();
    WaveFactory.closeSeriesViewer(this);

    GuiExecutor.execute(
        () -> {
          if (ecgView != null) {
            ecgView.dispose();
          }
        });
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt instanceof ObservableEvent event) {
      ObservableEvent.BasicAction action = event.getActionCommand();
      Object newVal = event.getNewValue();

      if (ObservableEvent.BasicAction.REMOVE.equals(action)) {
        if (newVal instanceof DicomSeries) {
          if (ecgView != null && ecgView.getSeries() == newVal) {
            close();
          }
        } else if (newVal instanceof MediaSeriesGroup group) {
          // Patient Group
          if (TagD.getUID(Level.PATIENT).equals(group.getTagID())) {
            if (group.equals(getGroupID())) {
              // Close the content of the plug-in
              close();
            }
          }
          // Study Group
          else if (TagD.getUID(Level.STUDY).equals(group.getTagID())
              && event.getSource() instanceof DicomModel model) {
            for (MediaSeriesGroup s : model.getChildren(group)) {
              if (ecgView != null && ecgView.getSeries() == s) {
                close();
                break;
              }
            }
          }
        }
      }
    }
  }

  @Override
  public int getViewTypeNumber(GridBagLayoutModel layout, Class<?> defaultClass) {
    return 0;
  }

  @Override
  public boolean isViewType(Class<?> defaultClass, String type) {
    if (defaultClass != null) {
      try {
        Class<?> clazz = Class.forName(type);
        return defaultClass.isAssignableFrom(clazz);
      } catch (Exception e) {
        LOGGER.error("Checking view type", e);
      }
    }
    return false;
  }

  @Override
  public DefaultView2d<DicomImageElement> createDefaultView(String classType) {
    return null;
  }

  @Override
  public JComponent createComponent(String clazz) {
    try {
      Class<?> cl = Class.forName(clazz);
      JComponent component = (JComponent) cl.newInstance();
      if (component instanceof SeriesViewerListener viewerListener) {
        eventManager.addSeriesViewerListener(viewerListener);
      }
      if (component instanceof WaveView waveView) {
        ecgView = waveView;
      }
      return component;
    } catch (Exception e) {
      LOGGER.error("Cannot create {}", clazz, e);
    }
    return null;
  }

  @Override
  public Class<?> getSeriesViewerClass() {
    return WaveView.class;
  }

  @Override
  public GridBagLayoutModel getDefaultLayoutModel() {
    return DEFAULT_VIEW;
  }

  @Override
  public List<Action> getPrintActions() {
    ArrayList<Action> actions = new ArrayList<>(1);
    final String title = Messages.getString("ECGontainer.print_layout");

    AbstractAction printStd =
        new AbstractAction(title, ResourceUtil.getIcon(ActionIcon.PRINT)) {

          @Override
          public void actionPerformed(ActionEvent e) {
            printCurrentView();
          }
        };
    actions.add(printStd);

    return actions;
  }

  @Override
  public void addSeries(MediaSeries<DicomImageElement> sequence) {
    if (ecgView != null
        && sequence instanceof Series<?> series
        && ecgView.getSeries() != sequence) {
      ecgView.setSeries(series);
    }
  }

  @Override
  public void addSeriesList(
      List<MediaSeries<DicomImageElement>> seriesList, boolean removeOldSeries) {
    if (seriesList != null && !seriesList.isEmpty()) {
      addSeries(seriesList.get(0));
    }
  }

  @Override
  public void selectLayoutPositionForAddingSeries(List<MediaSeries<DicomImageElement>> seriesList) {
    // Do it in addSeries()
  }

  @Override
  public List<SynchView> getSynchList() {
    return SYNCH_LIST;
  }

  @Override
  public List<GridBagLayoutModel> getLayoutList() {
    return LAYOUT_LIST;
  }

  public void setZoomRatio(double ratio) {
    if (ecgView != null) {
      ecgView.setZoomRatio(ratio);
      ecgView.setFormat(ecgView.getCurrentFormat());
      ecgView.repaint();
    }
  }

  public void clearMeasurements() {
    if (ecgView != null) {
      ecgView.clearMeasurements();
    }
  }

  public void displayHeader() {
    if (ecgView != null) {
      DicomSpecialElement dcm =
          DicomModel.getFirstSpecialElement(ecgView.getSeries(), DicomSpecialElement.class);
      DicomFieldsView.showHeaderDialog(this, ecgView.getSeries(), dcm);
    }
  }

  void printCurrentView() {
    if (ecgView != null) {
      PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
      PrinterJob pj = PrinterJob.getPrinterJob();
      pj.setJobName(ecgView.getSeries().toString());

      // Get page format from the printer
      if (pj.printDialog(aset)) {
        // Force printing in black and white
        PageFormat pageFormat = pj.getPageFormat(aset);
        Paper paper = pageFormat.getPaper();
        double margin = 12;
        paper.setImageableArea(
            margin, margin, paper.getWidth() - margin * 2, paper.getHeight() - margin * 2);
        pageFormat.setPaper(paper);
        DefaultPrinter pnlPreview = new DefaultPrinter(ecgView, pageFormat);
        pj.setPrintable(pnlPreview, pageFormat);
        try {
          pj.print();
        } catch (PrinterException e) {
          // check for the annoying 'Printer is not accepting job' error.
          if (e.getMessage().contains("accepting job")) { // NON-NLS
            // recommend prompting the user at this point if they want to force it,
            // so they'll know there may be a problem.
            int response =
                JOptionPane.showConfirmDialog(
                    GuiUtils.getUICore().getApplicationWindow(),
                    org.weasis.core.Messages.getString("ImagePrint.issue_desc"),
                    org.weasis.core.Messages.getString("ImagePrint.status"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (response == 0) {
              try {
                // try printing again but ignore the not-accepting-jobs attribute
                ForcedAcceptPrintService.setupPrintJob(pj); // add secret ingredient
                pj.print(aset);
                LOGGER.info("Bypass Printer is not accepting job");
              } catch (PrinterException ex) {
                LOGGER.error("Printer exception", ex);
              }
            }
          } else {
            LOGGER.error("Print exception", e);
          }
        }
      }
    }
  }
}
