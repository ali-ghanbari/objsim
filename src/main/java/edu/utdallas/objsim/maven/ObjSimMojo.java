/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */

package edu.utdallas.objsim.maven;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
@Mojo(name = "validate", requiresDependencyResolution = ResolutionScope.TEST)
public class ObjSimMojo extends AbstractObjSimMojo {

}
