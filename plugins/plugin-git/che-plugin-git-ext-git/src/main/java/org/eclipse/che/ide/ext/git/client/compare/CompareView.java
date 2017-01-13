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
package org.eclipse.che.ide.ext.git.client.compare;

import com.google.inject.ImplementedBy;

import org.eclipse.che.ide.api.mvp.View;

/**
 * The view of {@link ComparePresenter}.
 *
 * @author Igor Vinokur
 */
@ImplementedBy(CompareViewImpl.class)
interface CompareView extends View<CompareView.ActionDelegate> {

    /**
     * Set a title for the window.
     *
     * @param title
     *         text that will be as a title in the window
     */
    void setTitle(String title);

    void setColoumnTitles(String leftTitle, String rightTitle);

    /** Hide compare window. */
    void hide();

    /**
     * Show compare window with specified contents.
     *
     * @param oldContent
     *         content from specified leftTitle or branch
     * @param newContent
     *         content of current file
     * @param revisionA
     *         leftTitle or branch which is getting part in comparing
     * @param revisionB
     *         leftTitle or branch which is getting part in comparing
     * @param fileName
     *         changed file name with its full path
     */
    void show(String oldContent, String newContent, String fileName, boolean readOnly);

    interface ActionDelegate {
        /** Performs some actions in response to user's closing the window.
         *
         * @param newContent new content of compare widget
         */
        void onClose(String newContent);
    }
}
