package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.user.Group;
import org.apache.commons.collections4.SetUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Get all users who has edit permission on wiki objects.
 * @param <E> The confluence entity (space or page)
 * @param <K> The confluence entity key type
 */
abstract class PermissionService<E, K> {

    private final UserAccessor userAccessor;

    PermissionService(UserAccessor userAccessor) {
        this.userAccessor = userAccessor;
    }

    /**
     * For each page, get all users who:
     * <li>Belong to groups which has edit permission</li>
     * <li>Are granted edit permission as individual users</li>
     */
    final Map<K, Set<UserKey>> getPermissions(Collection<PageData> pageDataList) {
        final List<E> entities = convert(pageDataList);

        final Set<String> groupNames = getEditGroups(entities);

        final Map<String, Set<UserKey>> editUsersByGroupName = getEditUserByGroup(groupNames);

        return entities.stream().collect(Collectors.toMap(
                this::key,
                entity -> {
                    final Set<UserKey> editUsers = getEditUsers(entity);
                    final Set<String> groups = getEditGroups(entity);
                    final Set<UserKey> editUsersFromGroups = groups.stream()
                            .filter(editUsersByGroupName::containsKey)
                            .map(editUsersByGroupName::get)
                            .flatMap(Set::stream)
                            .collect(Collectors.toSet());
                    return SetUtils.union(editUsers, editUsersFromGroups);
                }
        ));
    }

    /**
     * Mapping from a group to its users who have edit perm.
     */
    private Map<String, Set<UserKey>> getEditUserByGroup(Set<String> groupNames) {
        return groupNames.stream().collect(Collectors.toMap(
                Function.identity(),
                groupName -> {
                    final Group group = userAccessor.getGroup(groupName);
                    final Spliterator<ConfluenceUser> spliterator = Spliterators.spliteratorUnknownSize(
                            userAccessor.getMembers(group).iterator(),
                            Spliterator.ORDERED
                    );
                    return StreamSupport.stream(spliterator, false)
                            .map(ConfluenceUser::getKey)
                            .collect(Collectors.toSet());
                }
        ));
    }

    private Set<String> getEditGroups(List<E> entities) {
        return entities.stream()
                .map(this::getEditGroups)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    /**
     * How to retrieve entity key?
     */
    abstract K key(E entity);

    /**
     * Get the list of entities where the permission will be extracted from.
     */
    abstract List<E> convert(Collection<PageData> pageDataList);

    /**
     * Get all groups which have edit permission on an entity.
     */
    abstract Set<String> getEditGroups(E entity);

    /**
     * Get all individual users which have edit permission on an entity.
     */
    abstract Set<UserKey> getEditUsers(E entity);
}
