/*******************************************************************************
 * Copyright (c) 2016 iSencia Belgium NV.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Erwin De Ley - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.triquetrum.workflow.editor.palette.spi;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.ContributorFactorySimple;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IContributor;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.InvalidRegistryObjectException;

/**
 *
 * TODO : finalize contributor setup in combination with usage of SVG or Ptolemy actor icons (see TriqFeatureProvider.buildCreateFeature()).
 */
public class PaletteConfigurationElement implements IConfigurationElement {

  public static final String CLASS = "class";
  public static final String DISPLAY_NAME = "displayName";
  public static final String ICON = "icon";
  public static final String ICON_TYPE = "iconType";
  public static final String PRIORITY = "priority";
  public static final String PROPERTY = "property";
  public static final String PROVIDER = "provider";
  public static final String TYPE = "type";

  private final String name;
  private IContributor contributor;
  private final Map<String, String> attributes = new HashMap<>();

  /**
   * Constructor for manually creating a PaletteConfigurationElement.
   *
   * This can be used in {@link PaletteEntryProvider} implementations.
   *
   * @param name
   * @param contributorName
   * @param attributes
   */
  public PaletteConfigurationElement(String name, String contributorName, Map<String, String> attributes) {
    this.name = name;
    if(contributorName!=null) {
      contributor = ContributorFactorySimple.createContributor(contributorName);
    }
    if (attributes != null) {
      this.attributes.putAll(attributes);
    }
  }

  @Override
  public Object createExecutableExtension(String propertyName) throws CoreException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getAttribute(String name) throws InvalidRegistryObjectException {
    return attributes.get(name);
  }

  @Override
  public String getAttribute(String attrName, String locale) throws InvalidRegistryObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getAttributeAsIs(String name) throws InvalidRegistryObjectException {
    return attributes.get(name);
  }

  @Override
  public String[] getAttributeNames() throws InvalidRegistryObjectException {
    return attributes.keySet().toArray(new String[0]);
  }

  @Override
  public IConfigurationElement[] getChildren() throws InvalidRegistryObjectException {
    return new IConfigurationElement[0];
  }

  @Override
  public IConfigurationElement[] getChildren(String name) throws InvalidRegistryObjectException {
    return new IConfigurationElement[0];
  }

  @Override
  public IExtension getDeclaringExtension() throws InvalidRegistryObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getName() throws InvalidRegistryObjectException {
    return name;
  }

  @Override
  public Object getParent() throws InvalidRegistryObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getValue() throws InvalidRegistryObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getValue(String locale) throws InvalidRegistryObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getValueAsIs() throws InvalidRegistryObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getNamespace() throws InvalidRegistryObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getNamespaceIdentifier() throws InvalidRegistryObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public IContributor getContributor() throws InvalidRegistryObjectException {
    return contributor;
  }

  @Override
  public boolean isValid() {
    return true;
  }
}
