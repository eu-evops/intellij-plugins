package com.intellij.coldFusion.UI.runner;


import com.intellij.coldFusion.CfmlBundle;
import com.intellij.coldFusion.model.files.CfmlFileType;
import com.intellij.coldFusion.model.files.CfmlFileViewProvider;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.ide.scratch.ScratchFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;


/**
 * Created by karashevich on 02/03/16.
 */
public class CfmlRunConfigurationProducer extends RunConfigurationProducer<CfmlRunConfiguration> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.coldFusion.UI.runner.CfmlRunConfigurationProducer");
  public final static String WWW_ROOT = "wwwroot";
  public final static String DEFAULT_HOST = "http://localhost:8500";

  public CfmlRunConfigurationProducer() {
    super(CfmlRunConfigurationType.
      getInstance());
  }

  @Override
  protected boolean setupConfigurationFromContext(CfmlRunConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {

    final Location location = context.getLocation();
    if (!(location instanceof PsiLocation)) return false;

    final VirtualFile file;
    PsiElement element = location.getPsiElement();
    final PsiFile containingFile = element.getContainingFile();
    if (isValid(containingFile)) {
      file = containingFile.getVirtualFile();
      sourceElement.set(containingFile);
    } else {
      return false;
    }

    if (!FileTypeManager.getInstance().isFileOfType(file, ScratchFileType.INSTANCE)) {
      final VirtualFile root = ProjectRootManager.getInstance(element.getProject()).getFileIndex().getContentRootForFile(file);
      if (root == null) return false;
    }

    if (configuration == null) {
      return false;
    }
    CfmlRunnerParameters params = configuration.getRunnerParameters();

    String urlStr = configuration.getRunnerParameters().getUrl();
    String serverUrl;

    if (!urlStr.isEmpty()) {    //generated from default run configuration
      try {
        URL url = new URL(urlStr);
        serverUrl = url.getProtocol() + "://" + url.getAuthority();
      }
      catch (MalformedURLException e) {
        LOG.error(CfmlBundle.message("cfml.producer.error.url", urlStr));
        return false;
      }
    }
    else {    // if default configuration is not defined
      serverUrl = DEFAULT_HOST;
      configuration.setFromDefaultHost(true);
    }

    String path = buildPageUrl(context, file);

    //check that serverUrl ends with '/' and fix it if neccessary
    if (StringUtil.endsWith(serverUrl, "/") && !StringUtil.startsWith(path, "/")) {
      params.setUrl(serverUrl + path);
    }
    else if (!StringUtil.endsWith(serverUrl, "/") && !StringUtil.startsWith(path, "/")) {
      params.setUrl(serverUrl + "/" + path);
    }
    else if (!StringUtil.endsWith(serverUrl, "/") && StringUtil.startsWith(path, "/")) {
      params.setUrl(serverUrl + path);
    }
    else if (StringUtil.endsWith(serverUrl, "/") && StringUtil.startsWith(path, "/")) {
      params.setUrl(serverUrl + path.substring(1, path.length()));
    }

    configuration.setName(generateName(containingFile));
    return true;
  }

  @NotNull
  private static String generateName(PsiFile containingFile) {
    return StringUtil.isNotEmpty(containingFile.getVirtualFile().getPath()) ? PathUtil
      .getFileName(containingFile.getVirtualFile().getPath()) : "";
  }

  @NotNull
  private static String buildPageUrl(ConfigurationContext context, VirtualFile file) {
    String result;
    String absolutePageUrl = file.getUrl();
    int wwwrootIndex = absolutePageUrl.indexOf(WWW_ROOT);
    if (wwwrootIndex == -1) {
      VirtualFile projectBaseDir = context.getProject().getBaseDir();
      String relativePath = FileUtil.getRelativePath(projectBaseDir.getPath(), file.getPath(), '/');
      result = projectBaseDir.getName() + "/" + relativePath;
    }
    else {
      result = absolutePageUrl.substring(wwwrootIndex + WWW_ROOT.length());
    }
    return result;
  }

  @Override
  public boolean isConfigurationFromContext(CfmlRunConfiguration configuration, ConfigurationContext context) {
    final Location location = context.getLocation();
    if (location == null) return false;

    final PsiElement anchor = location.getPsiElement();
    final PsiFile containingFile = anchor.getContainingFile();
    if (isValid(containingFile)) {
      final String path;
      path = buildPageUrl(context, containingFile.getVirtualFile());
      URL url;
      final String urlFromRunnerParameters = configuration.getRunnerParameters().getUrl();
      if (urlFromRunnerParameters.isEmpty()) return false;
      try {
        url = new URL(urlFromRunnerParameters);
        return StringUtil.equals(url.getPath(), path);
      }
      catch (MalformedURLException e) {
        LOG.error(CfmlBundle.message("cfml.producer.error.url", urlFromRunnerParameters));
      }
    }
    return false;
  }

  private static boolean isValid(@Nullable PsiFile containingFile) {
    return containingFile != null &&
           (containingFile.getFileType() == CfmlFileType.INSTANCE || containingFile.getViewProvider() instanceof CfmlFileViewProvider) &&
           containingFile.getVirtualFile() != null;
  }
}