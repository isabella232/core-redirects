package com.tallence.core.redirects.studio.service;

import com.coremedia.cap.content.Content;
import com.coremedia.cap.content.ContentRepository;
import com.coremedia.cap.content.ContentType;
import com.coremedia.cap.content.authorization.AccessControl;
import com.coremedia.cap.content.authorization.Right;
import com.coremedia.cap.springframework.security.impl.CapUserDetails;
import com.coremedia.cap.user.Group;
import com.coremedia.cap.user.User;
import com.coremedia.cap.user.UserRepository;
import com.tallence.core.redirects.model.SourceUrlType;
import com.tallence.core.redirects.studio.model.RedirectUpdateProperties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.annotation.PostConstruct;

import static com.tallence.core.redirects.studio.model.RedirectUpdateProperties.SOURCE_URL_TYPE;

/**
 * Default implementation of a {@link RedirectPermissionService}.
 * With this implementation, the system checks whether the read and write authorizations are present on the folder when
 * creating, editing, deleting, and reading forwards. Redirects with a regex can only be edited, deleted or created by
 * administrators.
 */
public class RedirectPermissionServiceImpl implements RedirectPermissionService {

  private static final Logger LOG = LoggerFactory.getLogger(RedirectPermissionServiceImpl.class);

  private final ContentRepository contentRepository;
  private final UserRepository userRepository;
  private final String regexGroupName;
  private Group regexGroup;
  private ContentType redirectContentType;

  @Autowired
  public RedirectPermissionServiceImpl(ContentRepository contentRepository, UserRepository userRepository,
                                       @Value("${core.redirects.permissions.regexGroup:}") String regexGroupName) {
    this.contentRepository = contentRepository;
    this.userRepository = userRepository;
    this.redirectContentType = contentRepository.getContentType("Redirect");
    this.regexGroupName = regexGroupName;
  }

  @Override
  public boolean mayRead(Content rootFolder) {
    return contentRepository.getAccessControl().mayPerform(rootFolder, this.redirectContentType, Right.READ);
  }

  @Override
  public boolean mayCreate(Content rootFolder, RedirectUpdateProperties updateProperties) {
    return mayPerformWriteAndPublish(rootFolder) && isAllowedForRegex(isUserAllowedForRegex(), updateProperties.getSourceUrlType());
  }

  @Override
  public boolean mayDelete(Content redirect) {
    //Only admins may delete regex redirects
    boolean administrator = isUserAllowedForRegex();
    return mayPerformDeleteAndPublish(redirect) &&
            isAllowedForRegex(administrator, SourceUrlType.asSourceUrlType(redirect.getString(SOURCE_URL_TYPE)));
  }

  @Override
  public boolean mayWrite(Content redirect, RedirectUpdateProperties updateProperties) {
    //Only admins may edit regex redirects
    boolean administrator = isUserAllowedForRegex();
    return mayPerformWriteAndPublish(redirect) &&
            isAllowedForRegex(administrator, updateProperties.getSourceUrlType()) &&
            isAllowedForRegex(administrator, SourceUrlType.asSourceUrlType(redirect.getString(SOURCE_URL_TYPE)));
  }

  @Override
  public RedirectRights resolveRights(Content rootFolder) {
    return new RedirectRights(mayPerformWriteAndPublish(rootFolder), isUserAllowedForRegex());
  }

  private boolean isAllowedForRegex(boolean mayUseRegex, SourceUrlType sourceType) {
    return mayUseRegex || !SourceUrlType.REGEX.equals(sourceType);
  }

  private boolean mayPerformWriteAndPublish(Content content) {
    AccessControl accessControl = contentRepository.getAccessControl();
    return accessControl.mayPerform(content, redirectContentType, Right.WRITE) &&
            accessControl.mayPerform(content, redirectContentType, Right.PUBLISH);
  }

  private boolean mayPerformDeleteAndPublish(Content content) {
    AccessControl accessControl = contentRepository.getAccessControl();
    return accessControl.mayPerform(content, redirectContentType, Right.DELETE) &&
            accessControl.mayPerform(content, redirectContentType, Right.PUBLISH);
  }

  private boolean isUserAllowedForRegex() {
    User user = userRepository.getUser(getUserId());
    if (user == null) {
      throw new IllegalStateException("No user could be found");
    }

    if (regexGroup != null) {
      return user.isMemberOf(regexGroup);
    } else {
      return user.isAdministrative();
    }
  }

  private String getUserId() {
    Object user = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (user instanceof CapUserDetails) {
      return ((CapUserDetails) user).getUserId();
    } else {
      throw new IllegalStateException("Could not get userId from authenticated user.");
    }
  }

  @PostConstruct
  public void postConstruct() {
    if (StringUtils.isNotBlank(regexGroupName)) {
      regexGroup = userRepository.getGroupByName(regexGroupName);
      if (regexGroup == null) {
        LOG.error("Configured regexGroup [{}] not found in CMS!", regexGroupName);
      }
    }
  }
}
