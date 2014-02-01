/*
 * Copyright (C) 2010-2014 Serge Rieder
 * serge@jkiss.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.jkiss.dbeaver.ext.erd.editor;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.gef.EditPart;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.IDataSourceContainerProvider;
import org.jkiss.dbeaver.ext.erd.model.DiagramLoader;
import org.jkiss.dbeaver.ext.erd.model.ERDObject;
import org.jkiss.dbeaver.ext.erd.model.EntityDiagram;
import org.jkiss.dbeaver.ext.erd.part.DiagramPart;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSDataSourceContainer;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.runtime.DefaultProgressMonitor;
import org.jkiss.dbeaver.runtime.load.AbstractLoadService;
import org.jkiss.dbeaver.runtime.load.LoadingUtils;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.utils.ContentUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

/**
 * Standalone ERD editor
 */
public class ERDEditorStandalone extends ERDEditorPart implements IDataSourceContainerProvider, IResourceChangeListener {

    /**
     * No-arg constructor
     */
    public ERDEditorStandalone()
    {
    }

    @Override
    public void dispose()
    {
        super.dispose();
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
    }

    @Override
    public void init(IEditorSite site, IEditorInput input) throws PartInitException
    {
        super.init(site, input);
        ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
    }

    @Override
    public boolean isReadOnly()
    {
        return false;
    }

    @Override
    public void createPartControl(Composite parent)
    {
        super.createPartControl(parent);

        loadDiagram();
    }

    @Override
    public void doSave(IProgressMonitor monitor)
    {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DiagramLoader.save(new DefaultProgressMonitor(monitor), getDiagramPart(), getDiagram(), false, buffer);

            final IFile file = getEditorFile();
            file.setContents(new ByteArrayInputStream(buffer.toByteArray()), true, true, monitor);

            getCommandStack().markSaveLocation();
        } catch (Exception e) {
            UIUtils.showErrorDialog(getSite().getShell(), "Save diagram", null, e);
        }
    }

/*
    protected void createActions()
    {
        super.createActions();

        //addEditorAction(new SaveAction(this));
    }
*/

    @Override
    protected synchronized void loadDiagram()
    {
        if (diagramLoadingJob != null) {
            // Do not start new one while old is running
            return;
        }
        diagramLoadingJob = LoadingUtils.createService(
            new AbstractLoadService<EntityDiagram>("Load diagram '" + getEditorInput().getName() + "'") {
                @Override
                public EntityDiagram evaluate()
                    throws InvocationTargetException, InterruptedException
                {
                    try {
                        return loadContentFromFile(getProgressMonitor());
                    } catch (DBException e) {
                        log.error(e);
                    }

                    return null;
                }

                @Override
                public Object getFamily()
                {
                    return ERDEditorStandalone.this;
                }
            },
            progressControl.createLoadVisualizer());
        diagramLoadingJob.addJobChangeListener(new JobChangeAdapter() {
            @Override
            public void done(IJobChangeEvent event)
            {
                diagramLoadingJob = null;
            }
        });
        diagramLoadingJob.schedule();

        setPartName(getEditorInput().getName());
    }

    private EntityDiagram loadContentFromFile(DBRProgressMonitor progressMonitor)
        throws DBException
    {
        final IFile file = getEditorFile();

        final DiagramPart diagramPart = getDiagramPart();
        EntityDiagram entityDiagram = new EntityDiagram(null, file.getName());
        entityDiagram.clear();
        entityDiagram.setLayoutManualAllowed(true);
        entityDiagram.setLayoutManualDesired(true);
        diagramPart.setModel(entityDiagram);

        try {
            final InputStream fileContent = file.getContents();
            try {
                DiagramLoader.load(progressMonitor, file.getProject(), diagramPart, fileContent);
            } finally {
                ContentUtils.close(fileContent);
            }
        } catch (Exception e) {
            log.error("Error loading ER diagram from '" + file.getName() + "'", e);
        }

        return entityDiagram;
    }

    private IFile getEditorFile()
    {
        return ContentUtils.getFileFromEditorInput(getEditorInput());
    }

    @Override
    public DBSDataSourceContainer getDataSourceContainer()
    {
        for (Object part : getViewer().getSelectedEditParts()) {
            EditPart editPart = (EditPart) part;
            if (editPart.getModel() instanceof ERDObject) {
                final ERDObject model = (ERDObject) editPart.getModel();
                if (model.getObject() instanceof DBSObject) {
                    DBSObject dbObject = (DBSObject) model.getObject();
                    if (dbObject.getDataSource() != null) {
                        return dbObject.getDataSource().getContainer();
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void resourceChanged(IResourceChangeEvent event)
    {
        IResourceDelta delta= event.getDelta();
        if (delta == null) {
            return;
        }
        final IFile file = getEditorFile();
        delta = delta.findMember(file.getFullPath());
        if (delta == null) {
            return;
        }
        if (delta.getKind() == IResourceDelta.REMOVED) {
            // Refresh editor
            Display.getDefault().asyncExec(new Runnable() {
                @Override
                public void run()
                {
                    getSite().getWorkbenchWindow().getActivePage().closeEditor(ERDEditorStandalone.this, false);
                }
            });
        }
    }

}
