/*******************************************************************************
 * Copyright (c) 2017 iSencia Belgium NV.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Erwin De Ley - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.triquetrum.workflow.editor.palette;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.triquetrum.workflow.editor.TriqEditorPlugin;
import org.eclipse.triquetrum.workflow.model.util.PtolemyUtil;
import org.eclipse.ui.statushandlers.StatusManager;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ptolemy.actor.gui.Configuration;
import ptolemy.data.expr.FileParameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.Entity;
import ptolemy.kernel.util.ChangeListener;
import ptolemy.kernel.util.ChangeRequest;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.NamedObj;
import ptolemy.kernel.util.Workspace;
import ptolemy.moml.CollectingMomlParsingErrorHandler;
import ptolemy.moml.CollectingMomlParsingErrorHandler.ErrorItem;
import ptolemy.moml.EntityLibrary;
import ptolemy.moml.ErrorHandler;
import ptolemy.moml.MoMLChangeRequest;
import ptolemy.moml.MoMLParser;

/**
 * A class that groups all services related to maintaining/modifying/... the user actor library that is available in the IDE.
 * <p>
 * REMARK : current implementation assumes that a separate instance is created for separate threads that need to check/modify libraries (if this would ever
 * occur).
 * 
 */
public class LibraryManager implements EventHandler {

  private final static Logger logger = LoggerFactory.getLogger(LibraryManager.class);

  private final static String USER_LIBRARY_NAME = "UserLibrary";
  private final static String ACTOR_LIBRARY_NAME = "actor library";
  private final static int ACTORS_LIBRARY_PREFIX_LENGTH = (".configuration." + ACTOR_LIBRARY_NAME + ".").length();
  private final static String SOURCE_PATH_LIB_ATTR_NAME = "_sourcePath";

  private static LibraryManager instance;
  
  private SortedMap<String, EntityLibrary> userLibraryMap = new TreeMap<String, EntityLibrary>();
  private Configuration configuration;

  public void activate() {
    File userHome = new File(System.getProperty("user.home"));
    File triqUserHome = new File(userHome, ".triquetrum");

    try {
      Configuration ptCfg = new Configuration(new Workspace());
      EntityLibrary actorLibrary = new EntityLibrary(ptCfg, "actor library");
      String userLibraryFilePath = new File(triqUserHome, "UserLibrary.xml").toURI().toString();
      actorLibrary.configure(null, userLibraryFilePath, null);
      actorLibrary.setClassDefinition(true);
//      actorLibrary.instantiate(null, userLibrary.getName());
      refreshManagerCache(ptCfg);
      try {
        new FileParameter(getUserLibrary(), "_sourcePath").setExpression(userLibraryFilePath);
      } catch (Exception e) {
        // TODO ignore as this would imply that the sourcePath is already present, which is fine.
        e.printStackTrace();
      }
      instance = this;
    } catch (IllegalActionException | NameDuplicationException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public static LibraryManager getDefaultInstance() {
    return instance;
  }
  
  public EntityLibrary getUserLibrary() {
    return userLibraryMap.get(USER_LIBRARY_NAME);
  }

  /**
   * UPdates the cached info
   * 
   * @param configuration
   */
  public void refreshManagerCache(Configuration configuration) {
    if (configuration != this.configuration) {
      if (configuration != null) {
        refreshUserLibraryMap(configuration);
      } else {
        if (userLibraryMap != null) {
          userLibraryMap.clear();
          userLibraryMap = null;
        }
      }
      this.configuration = configuration;
    }
  }

  private void refreshUserLibraryMap(Configuration configuration) {
    if (userLibraryMap == null) {
      userLibraryMap = new TreeMap<String, EntityLibrary>();
    } else {
      userLibraryMap.clear();
    }
    CompositeEntity topLibrary = (CompositeEntity) configuration.getEntity("actor library");
    if (topLibrary != null) {
      EntityLibrary userLibrary = (EntityLibrary) topLibrary.getEntity(USER_LIBRARY_NAME);
      if (userLibrary != null) {
        // during start-up the userlibrary may not yet be in the configuration...
        userLibraryMap.put(USER_LIBRARY_NAME, userLibrary);
        List<EntityLibrary> libraries = userLibrary.entityList(EntityLibrary.class);
        CollectingMomlParsingErrorHandler errorHandler = new CollectingMomlParsingErrorHandler();

        // no filter needed on sourcepath attr or whatever
        // userlibrary sublibraries are maintained in the 1 single UserLibrary.xml file
        List<EntityLibrary> deepLibraries = getDeepLibrariesWithAttributes(libraries, errorHandler);
        for (EntityLibrary lib : deepLibraries) {
          String name = lib.getFullName().substring(ACTORS_LIBRARY_PREFIX_LENGTH);
          userLibraryMap.put(name, lib);
        }

        if (errorHandler.hasErrors()) {
          MultiStatus status = new MultiStatus(TriqEditorPlugin.getID(), IStatus.ERROR, "Some Library entries could not be constructed.", null);
          for (ErrorItem errorItem : errorHandler) {
            status.add(new Status(IStatus.ERROR, TriqEditorPlugin.getID(), "Error populating library " + userLibrary.getFullName(), errorItem.exception));
          }
          StatusManager.getManager().handle(status, StatusManager.BLOCK);
        }
      }
    }
  }

  /**
   * @return the names of the user library and its sublibraries
   */
  public String[] getUserLibraryNames() {
    if (userLibraryMap == null || userLibraryMap.isEmpty())
      refreshUserLibraryMap(configuration);
    if (userLibraryMap == null) {
      // means the refresh failed
      return new String[0];
    } else {
      Set<String> libraryNames = userLibraryMap.keySet();
      return libraryNames.toArray(new String[0]);
    }
  }

  public void addSubLibrary(final EntityLibrary library, String folderName) throws NameDuplicationException, IllegalActionException {
    EntityLibrary subLibrary = new EntityLibrary(library, folderName);
    StringWriter buffer = new StringWriter();
    try {
      subLibrary.exportMoML(buffer, 1);
    } catch (IOException e) {
      // ignore, will never happen for a StringWriter
    }
    subLibrary.setName(folderName);
    ChangeRequest request = new MoMLChangeRequest(this, library, buffer.toString()) {
      @Override
      public NamedObj getLocality() {
        return library;
      }
    };
    request.addChangeListener(new EntityLibraryChangedListener(this));
    library.requestChange(request);

  }

  public void renameLibrary(final EntityLibrary library, String newName) {
    String oldName = library.getName();
    StringBuffer moml = new StringBuffer("<");
    String elementName = library.getElementName();
    moml.append(elementName);
    moml.append(" name=\"");
    moml.append(oldName);
    moml.append("\">");
    if (!oldName.equals(newName)) {
      moml.append("<rename name=\"");
      moml.append(newName);
      moml.append("\"/>");
    }

    moml.append("</");
    moml.append(elementName);
    moml.append(">");

    NamedObj parent = library.getContainer();
    MoMLChangeRequest request = new MoMLChangeRequest(this, // originator
        parent, // context
        moml.toString(), // MoML code
        null) /* base */ {
      @Override
      public NamedObj getLocality() {
        return library;
      }
    };

    request.addChangeListener(new EntityLibraryChangedListener(this));
    request.setUndoable(true);
    parent.requestChange(request);
  }

  public void saveChangedEntityLibrary(EntityLibrary library) {
    FileWriter w = null;
    FileParameter libSourceFileParameter = (FileParameter) library.getAttribute(SOURCE_PATH_LIB_ATTR_NAME);
    if (libSourceFileParameter != null) {
      try {
        URL libURL = libSourceFileParameter.asURL();
        w = new FileWriter(libURL.getFile());
        library.exportMoML(w, 0);
        if (logger.isDebugEnabled())
          logger.debug("Saved modified library ");
      } catch (Exception e) {
        logger.error("Failed to save update in " + libSourceFileParameter, e);
        StatusManager.getManager().handle(new Status(IStatus.ERROR, TriqEditorPlugin.getID(), "Failed to save update in " + libSourceFileParameter, e),
            StatusManager.BLOCK);
      } finally {
        if (w != null)
          try {
            w.close();
          } catch (IOException e1) {
          }
      }
    } else if (library.getContainer() instanceof EntityLibrary) {
      // try to save the container (i.e. hope this library can be saved in
      // a moml of the container)
      saveChangedEntityLibrary((EntityLibrary) library.getContainer());
    } else {
      StatusManager.getManager()
          .handle(new Status(IStatus.ERROR, TriqEditorPlugin.getID(),
              "Library update not supported for library " + library.getName() + "\n" + SOURCE_PATH_LIB_ATTR_NAME + " attribute missing in library cfg file."),
              StatusManager.BLOCK);
    }
  }

  public void saveEntityInDefaultUserLibrary(Entity entity) throws Exception {
    EntityLibrary library = getUserLibrary();
    if (library == null) {
      StatusManager.getManager().handle(
          new Status(IStatus.ERROR, TriqEditorPlugin.getID(), "Save In Library failed: " + "Could not find default user library."),
          StatusManager.BLOCK);
      return;
    }
    saveEntityInLibrary(library, entity);
  }

  public void saveEntityInUserLibrary(String libraryName, Entity entity) throws Exception {
    EntityLibrary library = (EntityLibrary) userLibraryMap.get(libraryName);
    if (library == null) {
      StatusManager.getManager().handle(
          new Status(IStatus.ERROR, TriqEditorPlugin.getID(), "Save In Library failed: " + "Could not find library with name \"" + libraryName + "\"."),
          StatusManager.BLOCK);
      return;
    }
    saveEntityInLibrary(library, entity);
  }

  public void saveEntityInLibrary(EntityLibrary library, Entity entity) throws Exception {
    // Check whether there is already something existing in the
    // library with this name.
    if (library.getEntity(entity.getName()) != null) {
      throw new Exception("An object with name " + entity.getName() + " already exists in the library " + library.getName());
    }

    Entity entityAsClass = (Entity) entity.clone(entity.workspace());
    entityAsClass.setClassDefinition(true);

    Entity instance = (Entity) entityAsClass.instantiate(library, entity.getName());
    instance.setClassName(entity.getClassName());

    StringWriter buffer = new StringWriter();
    try {
      instance.exportMoML(buffer, 1);
    } catch (IOException e) {
      // ignore, will never happen for a StringWriter
    }

    ChangeRequest request = new MoMLChangeRequest(instance, library, buffer.toString());
    request.addChangeListener(new EntityLibraryChangedListener(this));
    library.requestChange(request);
  }

  public void deleteEntityFromLibrary(final EntityLibrary library, final Entity entity) {
    // Check whether there is already something existing in the
    // library with this name.
    if (library.getEntity(entity.getName()) == null) {
      StatusManager.getManager().handle(
          new Status(IStatus.WARNING, TriqEditorPlugin.getID(),
              "Delete from Library failed: An object with name " + entity.getName() + " does not exist in the library " + library.getName()),
          StatusManager.SHOW);
      return;
    }

    ChangeRequest request = new MoMLChangeRequest(this, library, "<deleteEntity name=\"" + entity.getName() + "\"/>\n") {
      @Override
      public NamedObj getLocality() {
        return userLibraryMap.get(USER_LIBRARY_NAME);
      }
    };
    request.addChangeListener(new EntityLibraryChangedListener(this));
    library.requestChange(request);
  }

  /**
   * @param libraries
   *          the parent libraries in which we should look for sublibraries
   * @param attrNames
   *          filter, i.e. the method will only return the libraries that have attributes defined with the given names
   * @return
   */
  private List<EntityLibrary> getDeepLibrariesWithAttributes(List<EntityLibrary> libraries, CollectingMomlParsingErrorHandler errorHandler,
      String... attrNames) {
    List<EntityLibrary> deepLibraries = new ArrayList<EntityLibrary>();
    for (EntityLibrary library : libraries) {
      // libraries are lazy loaders, so we need to call populate()
      // explicitly here!
      ErrorHandler prevErrHandler = MoMLParser.getErrorHandler();
      try {
        // ensure no error pop-ups with annoying stack traces
        // are happening while populating the libraries
        // remark that as a result, a library containing entity
        // definitions
        // that fail to load, will after this saving operation no longer
        // contain these failed ones!
        MoMLParser.setErrorHandler(errorHandler);
        library.populate();
      } catch (Exception e) {
        // catch any problems during populating
        logger.error("Error populating library " + library.getFullName() + " : " + e.getMessage());
      } finally {
        MoMLParser.setErrorHandler(prevErrHandler);
      }
      List<EntityLibrary> sublibs = library.entityList(EntityLibrary.class);
      if (sublibs != null && sublibs.size() > 0) {
        deepLibraries.addAll(getDeepLibrariesWithAttributes(sublibs, errorHandler, attrNames));
      }
      // also show intermediate levels if they have the right attributes
      boolean accepted = true;
      for (String attrName : attrNames) {
        if (library.getAttribute(attrName) == null) {
          accepted = false;
          break;
        }
      }
      if (accepted) {
        deepLibraries.add(library);
      }
    }

    return deepLibraries;
  }

  @Override
  public void handleEvent(Event event) {
    if ("org/eclipse/triquetrum/workflow/userlibrary/add".equals(event.getTopic())) {
      // TODO create user library entry
      System.out.println(event);
      String clazz = (String) event.getProperty("class");
      String name = (String) event.getProperty("displayName");
      try {
        name = getUserLibrary().uniqueName(name);
        Entity addedActor = PtolemyUtil._createEntity(null, clazz, null, name);
        saveEntityInDefaultUserLibrary(addedActor);
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  public static class EntityLibraryChangedListener implements ChangeListener {
    private LibraryManager libraryManager;

    public EntityLibraryChangedListener(LibraryManager libraryManager) {
      this.libraryManager = libraryManager;
    }

    public void changeExecuted(ChangeRequest change) {
      EntityLibrary library = (EntityLibrary) ((MoMLChangeRequest) change).getContext();

      libraryManager.saveChangedEntityLibrary(library);
    }

    public void changeFailed(ChangeRequest change, Exception exception) {
      // TODO Auto-generated method stub

    }
  }
}
