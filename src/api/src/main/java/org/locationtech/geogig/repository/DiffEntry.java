/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.util.Comparator;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.CanonicalNodeOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Provides a way of describing the between two different {@link Node}s.
 */
public class DiffEntry {

    /**
     * The possible types of change between the two {@link Node}s
     */
    public static enum ChangeType {
        /**
         * Add a new Feature
         */
        ADDED {
            @Override
            public int value() {
                return 0;
            }
        },

        /**
         * Modify an existing Feature
         */
        MODIFIED {
            @Override
            public int value() {
                return 1;
            }
        },

        /**
         * Delete an existing Feature
         */
        REMOVED {
            @Override
            public int value() {
                return 2;
            }
        };

        public abstract int value();

        public static ChangeType valueOf(int value) {
            // relying in the enum ordinal, beware
            return ChangeType.values()[value];
        }
    }

    private final NodeRef oldObject;

    private final NodeRef newObject;

    /**
     * Constructs a new {@code DiffEntry} from two different {@link Node}s
     * 
     * @param oldObject the old node ref
     * @param newObject the new node ref
     */
    public DiffEntry(@Nullable NodeRef oldObject, @Nullable NodeRef newObject) {

        Preconditions.checkArgument(oldObject != null || newObject != null,
                "Either oldObject or newObject shall not be null");

        if (oldObject != null && oldObject.equals(newObject)) {
            throw new IllegalArgumentException(
                    "Trying to create a DiffEntry for the same object id, means the object didn't change: "
                            + oldObject.toString());
        }
        if (oldObject != null && newObject != null) {
            if (!oldObject.getType().equals(newObject.getType())) {
                String msg = String.format("Types don't match: %s : %s", oldObject.getType(),
                        newObject.getType());
                throw new IllegalArgumentException(msg);
            }
        }

        this.oldObject = oldObject;
        this.newObject = newObject;
    }

    /**
     * @return the id of the old version id of the object, or {@link ObjectId#NULL} if
     *         {@link #changeType()} is {@code ADD}
     */
    public ObjectId oldObjectId() {
        return oldObject == null ? ObjectId.NULL : oldObject.getObjectId();
    }

    /**
     * @return the old object, or {@code null} if {@link #changeType()} is {@code ADD}
     */
    public NodeRef getOldObject() {
        return oldObject;
    }

    public Optional<NodeRef> oldObject() {
        return Optional.fromNullable(oldObject);
    }

    /**
     * @return the id of the new version id of the object, or {@link ObjectId#NULL} if
     *         {@link #changeType()} is {@code DELETE}
     */
    public ObjectId newObjectId() {
        return newObject == null ? ObjectId.NULL : newObject.getObjectId();
    }

    /**
     * @return the id of the new version of the object, or {@code null} if {@link #changeType()} is
     *         {@code DELETE}
     */
    public NodeRef getNewObject() {
        return newObject;
    }

    public Optional<NodeRef> newObject() {
        return Optional.fromNullable(newObject);
    }

    /**
     * @return the type of change
     */
    public ChangeType changeType() {
        ChangeType type;
        if (oldObject == null || oldObject.getObjectId().isNull()) {
            type = ChangeType.ADDED;
        } else if (newObject == null || newObject.getObjectId().isNull()) {
            type = ChangeType.REMOVED;
        } else {
            type = ChangeType.MODIFIED;
        }

        return type;
    }

    /**
     * @return the {@code DiffEntry} in the form of a readable {@code String}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(changeType().toString());
        if (!isAdd()) {
            sb.append(" [").append(oldObject).append("]");
        }
        if (isChange()) {
            sb.append(" -> ");
        }
        if (!isDelete()) {
            sb.append(" [").append(newObject).append(']');
        }
        return sb.toString();
    }

    /**
     * @return the path of the old object
     */
    public @Nullable String oldPath() {
        return oldObject == null ? null : oldObject.path();
    }

    public String path() {
        return newObject == null ? oldObject.path() : newObject.path();
    }

    /**
     * @return the path of the new object
     */
    public @Nullable String newPath() {
        return newObject == null ? null : newObject.path();
    }

    /**
     * @return the name of the new object
     */
    public @Nullable String newName() {
        return newObject == null ? null : newObject.getNode().getName();
    }

    /**
     * @return the name of the old object
     */
    public @Nullable String oldName() {
        return oldObject == null ? null : oldObject.getNode().getName();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DiffEntry)) {
            return false;
        }
        DiffEntry de = (DiffEntry) o;
        return Objects.equal(oldObject, de.oldObject) && Objects.equal(newObject, de.newObject);
    }

    /**
     * 
     * @param target
     */
    public void expand(Envelope target) {
        if (oldObject != null) {
            oldObject.expand(target);
        }
        if (newObject != null) {
            newObject.expand(target);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(oldObject, newObject);
    }

    public boolean isDelete() {
        return ChangeType.REMOVED.equals(changeType());
    }

    public boolean isAdd() {
        return ChangeType.ADDED.equals(changeType());
    }

    public boolean isChange() {
        return ChangeType.MODIFIED.equals(changeType());
    }

    public static Comparator<DiffEntry> COMPARATOR = new Comparator<DiffEntry>() {

        @Override
        public int compare(DiffEntry left, DiffEntry right) {
            final NodeRef nodeRef1 = left.oldObject().or(left.newObject()).get();
            final NodeRef nodeRef2 = right.oldObject().or(right.newObject()).get();

            if (nodeRef1.getType().equals(nodeRef2.getType())) {
                return CanonicalNodeOrder.INSTANCE.compare(nodeRef1.getNode(), nodeRef2.getNode());
            }
            // one is a tree, the other a feature.
            // the tree comes first if it's a feature's parent
            boolean leftIsRightsParent = NodeRef.isChild(nodeRef1.path()/* parent */,
                    nodeRef2.path()/* child */);
            if (leftIsRightsParent) {
                return -1;
            }
            boolean rightIsLeftsParent = NodeRef.isDirectChild(nodeRef2.path()/* parent */,
                    nodeRef1.path()/* child */);
            if (rightIsLeftsParent) {
                return 1;
            }
            // feature wins (got a new tree on one end while all features of the previous tree
            // haven't been consumed)
            return nodeRef1.getType() == TYPE.FEATURE ? -1 : 1;
        }
    };

    public TYPE newObjectType() {
        return getNewObject().getType();
    }

    public TYPE oldObjectType() {
        return getOldObject().getType();
    }

    public ObjectId newMetadataId() {
        return getNewObject().getMetadataId();
    }

    public ObjectId oldMetadataId() {
        return getOldObject().getMetadataId();
    }
}
