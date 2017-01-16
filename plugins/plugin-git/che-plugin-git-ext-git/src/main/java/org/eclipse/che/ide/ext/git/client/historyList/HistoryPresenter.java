/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.ext.git.client.historyList;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.core.ErrorCodes;
import org.eclipse.che.api.git.shared.LogResponse;
import org.eclipse.che.api.git.shared.Revision;
import org.eclipse.che.api.git.shared.ShowFileContentResponse;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.dialogs.DialogFactory;
import org.eclipse.che.ide.api.git.GitServiceClient;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.ext.git.client.GitLocalizationConstant;
import org.eclipse.che.ide.ext.git.client.compare.ComparePresenter;
import org.eclipse.che.ide.ext.git.client.compare.FileStatus;
import org.eclipse.che.ide.ext.git.client.compare.changedList.ChangedListPresenter;
import org.eclipse.che.ide.resource.Path;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.eclipse.che.api.git.shared.DiffType.NAME_STATUS;
import static org.eclipse.che.ide.api.notification.StatusNotification.DisplayMode.NOT_EMERGE_MODE;
import static org.eclipse.che.ide.api.notification.StatusNotification.Status.FAIL;
import static org.eclipse.che.ide.ext.git.client.compare.FileStatus.defineStatus;
import static org.eclipse.che.ide.util.ExceptionUtils.getErrorCode;

/**
 * Presenter for displaying list of revisions for comparing selected with local changes.
 *
 * @author Igor Vinokur
 * @author Vlad Zhukovskyi
 */
@Singleton
public class HistoryPresenter implements HistoryView.ActionDelegate {
    private final ComparePresenter        comparePresenter;
    private final ChangedListPresenter    changedListPresenter;
    private final DialogFactory           dialogFactory;
    private final HistoryView             view;
    private final GitServiceClient        service;
    private final GitLocalizationConstant locale;
    private final AppContext              appContext;
    private final NotificationManager     notificationManager;

    private Revision       selectedRevision;
    private Project        project;
    private Path           selectedPath;
    private List<Revision> revisions;

    @Inject
    public HistoryPresenter(HistoryView view,
                            ComparePresenter comparePresenter,
                            ChangedListPresenter changedListPresenter,
                            GitServiceClient service,
                            GitLocalizationConstant locale,
                            NotificationManager notificationManager,
                            DialogFactory dialogFactory,
                            AppContext appContext) {
        this.view = view;
        this.comparePresenter = comparePresenter;
        this.changedListPresenter = changedListPresenter;
        this.dialogFactory = dialogFactory;
        this.service = service;
        this.locale = locale;
        this.appContext = appContext;
        this.notificationManager = notificationManager;

        this.view.setDelegate(this);
    }

    /** Open dialog and shows revisions to compare. */
    public void show() {
        this.project = appContext.getRootProject();
        this.selectedPath = appContext.getResource()
                                      .getLocation()
                                      .removeFirstSegments(project.getLocation().segmentCount())
                                      .removeTrailingSeparator();
        getRevisions();
    }

    /** {@inheritDoc} */
    @Override
    public void onCloseClicked() {
        view.close();
    }

    /** {@inheritDoc} */
    @Override
    public void onCompareClicked() {
        compare();
    }

    /** {@inheritDoc} */
    @Override
    public void onRevisionUnselected() {
        selectedRevision = null;
        view.setEnableCompareButton(false);
        view.setDescription(locale.viewCompareRevisionFullDescriptionEmptyMessage());
    }

    /** {@inheritDoc} */
    @Override
    public void onRevisionSelected(@NotNull Revision revision) {
        selectedRevision = revision;

        view.setEnableCompareButton(true);
    }

    /** {@inheritDoc} */
    @Override
    public void onRevisionDoubleClicked() {
        compare();
    }

    /** Get list of revisions. */
    private void getRevisions() {
        service.log(appContext.getDevMachine(), project.getLocation(), selectedPath.isEmpty() ? null : new Path[]{selectedPath}, false)
               .then(new Operation<LogResponse>() {
                   @Override
                   public void apply(LogResponse log) throws OperationException {
                       HistoryPresenter.this.revisions = log.getCommits();
                       view.setRevisions(log.getCommits());
                       view.showDialog();
                   }
               }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError error) throws OperationException {
                if (getErrorCode(error.getCause()) == ErrorCodes.INIT_COMMIT_WAS_NOT_PERFORMED) {
                    dialogFactory.createMessageDialog(locale.compareWithRevisionTitle(),
                                                      locale.initCommitWasNotPerformed(),
                                                      null).show();
                } else {
                    notificationManager.notify(locale.logFailed(), FAIL, NOT_EMERGE_MODE);
                }
            }
        });
    }

    private void compare() {
        final String revisionA = revisions.get(revisions.indexOf(selectedRevision) + 1).getId();
        final String revisionB = selectedRevision.getId();
        service.diff(appContext.getDevMachine(),
                     project.getLocation(),
                     singletonList(selectedPath.toString()),
                     NAME_STATUS,
                     true,
                     0,
                     revisionA,
                     revisionB)
               .then(new Operation<String>() {
                   @Override
                   public void apply(final String diff) throws OperationException {
                       if (diff.isEmpty()) {
                           dialogFactory.createMessageDialog(locale.compareMessageIdenticalContentTitle(),
                                                             locale.compareMessageIdenticalContentText(), null).show();
                       } else {
                           final String[] changedFiles = diff.split("\n");
                           final Path path = Path.valueOf(changedFiles[0].substring(2));
                           if (changedFiles.length == 1) {
                               service.showFileContent(appContext.getDevMachine(), project.getLocation(), path, revisionA)
                                      .then(new Operation<ShowFileContentResponse>() {
                                          @Override
                                          public void apply(final ShowFileContentResponse contentA) throws OperationException {
                                              service.showFileContent(appContext.getDevMachine(), project.getLocation(), path, revisionB)
                                                     .then(new Operation<ShowFileContentResponse>() {
                                                         @Override
                                                         public void apply(ShowFileContentResponse contentB) throws OperationException {
                                                             comparePresenter.show(revisionA,
                                                                                   revisionB,
                                                                                   diff.substring(2),
                                                                                   contentA.getContent(),
                                                                                   contentB.getContent());
                                                         }
                                                     });
                                          }
                                      });
                           } else {
                               Map<String, FileStatus.Status> items = new HashMap<>();
                               for (String item : changedFiles) {
                                   items.put(item.substring(2, item.length()), defineStatus(item.substring(0, 1)));
                               }
                               changedListPresenter.show(items, revisionA, revisionB, project);
                           }
                       }
                   }
               })
               .catchError(new Operation<PromiseError>() {
                   @Override
                   public void apply(PromiseError arg) throws OperationException {
                       notificationManager.notify(locale.diffFailed(), FAIL, NOT_EMERGE_MODE);
                   }
               });
    }
}