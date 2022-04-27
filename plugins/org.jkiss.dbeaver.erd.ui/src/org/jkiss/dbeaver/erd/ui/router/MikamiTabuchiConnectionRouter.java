/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2022 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.erd.ui.router;


import org.eclipse.draw2dl.*;
import org.eclipse.draw2dl.LayoutListener.Stub;
import org.eclipse.draw2dl.geometry.Point;
import org.eclipse.draw2dl.geometry.PointList;
import org.eclipse.draw2dl.geometry.PrecisionPoint;
import org.eclipse.draw2dl.geometry.Rectangle;

import java.util.*;


public class MikamiTabuchiConnectionRouter extends AbstractRouter {
    private boolean isDirty = false;
    private final Map<Connection, Object> constraintMap = new HashMap<>();
    private Map<IFigure, Rectangle> figuresToBounds;
    private Map<Connection, OrthogonalPath> connectionToPath;
    private final IFigure container;
    private final MikamiTabuchiRouter algorithm = new MikamiTabuchiRouter();
    private final Set<Connection> staleConnections = new HashSet<>();
    private boolean ignoreInvalidate;
    private Map<Connection, OrthogonalPath> connectionToPaths = new HashMap<>();
    private final LayoutListener listener = new MikamiTabuchiConnectionRouter.LayoutTracker();
    private final FigureListener figureListener = source -> {
        Rectangle newBounds = source.getBounds().getCopy();
        if (MikamiTabuchiConnectionRouter.this.algorithm.updateObstacle(MikamiTabuchiConnectionRouter.this.figuresToBounds.get(source), newBounds)) {
            MikamiTabuchiConnectionRouter.this.queueRerouting();
            MikamiTabuchiConnectionRouter.this.isDirty = true;
        }

        MikamiTabuchiConnectionRouter.this.figuresToBounds.put(source, newBounds);
    };


    public Object getConstraint(Connection connection) {
        return this.constraintMap.get(connection);
    }


    public void invalidate(Connection connection) {
        if (!this.ignoreInvalidate) {
            this.staleConnections.add(connection);
            this.isDirty = true;
        }
    }

    private void queueRerouting() {
        if (this.connectionToPaths != null && !this.connectionToPaths.isEmpty()) {
            try {
                this.ignoreInvalidate = true;
                ((Connection)this.connectionToPaths.keySet().iterator().next()).revalidate();
            } finally {
                this.ignoreInvalidate = false;
            }

        }
    }

    void addChild(IFigure child) {
        if (this.connectionToPath != null) {
            if (!this.figuresToBounds.containsKey(child)) {
                Rectangle bounds = child.getBounds().getCopy();
                this.algorithm.addObstacle(bounds);
                this.figuresToBounds.put(child, bounds);
                child.addFigureListener(this.figureListener);
                this.isDirty = true;
            }
        }
    }

    public MikamiTabuchiConnectionRouter(IFigure container) {
        this.container = container;
    }

    private void processStaleConnections() {
        Iterator<Connection> iter = this.staleConnections.iterator();
        if (iter.hasNext() && this.connectionToPath == null) {
            this.connectionToPath = new HashMap<>();
            this.hookAll();
        }

        OrthogonalPath path;
        for(; iter.hasNext(); this.isDirty |= path.isDirty()) {
            Connection conn = iter.next();
            path = this.connectionToPath.get(conn);
            if (path == null) {
                path = new OrthogonalPath(conn);
                this.connectionToPath.put(conn, path);
                this.algorithm.addPath(path);
            }

            List constraint = (List) this.getConstraint(conn);
            if (constraint == null) {
                constraint = Collections.EMPTY_LIST;
            }

            path.setStartPoint(conn.getSourceAnchor().getOwner().getBounds().getCenter());
            path.setEndPoint(conn.getTargetAnchor().getOwner().getBounds().getCenter());
            if (constraint.isEmpty()) {
                path.setBendPoints(null);
            } else {
                PointList bends = new PointList(constraint.size());
                for (Object o : constraint) {
                    Bendpoint bp = (Bendpoint) o;
                    bends.addPoint(bp.getLocation());
                }

                path.setBendPoints(bends);
            }
        }

        this.staleConnections.clear();
    }

    private void hookAll() {
        this.figuresToBounds = new HashMap<>();

        for(int i = 0; i < this.container.getChildren().size(); ++i) {
            this.addChild(this.container.getChildren().get(i));
        }

        this.container.addLayoutListener(this.listener);
    }

    @Override
    public void route(Connection connection) {
        if (this.isDirty) {
            this.ignoreInvalidate = true;
            this.processStaleConnections();
            this.isDirty = false;
            this.algorithm.setClientArea(container.getClientArea());
            List<OrthogonalPath> updated = this.algorithm.solve();
            for (OrthogonalPath path : updated) {
                if (path == null || path.getBendPoints() == null) {
                    continue;
                }
                Connection current = path.getConnection();
                current.revalidate();
                PointList points = path.getBendPoints().getCopy();
                Point ref1 = new PrecisionPoint(points.getPoint(1));
                Point ref2 = new PrecisionPoint(points.getPoint(points.size() - 2));
                current.translateToAbsolute(ref1);
                current.translateToAbsolute(ref2);
                Point start = current.getSourceAnchor().getLocation(ref1).getCopy();
                Point end = current.getTargetAnchor().getLocation(ref2).getCopy();
                current.translateToRelative(start);
                current.translateToRelative(end);
                points.setPoint(start, 0);
                points.setPoint(end, points.size() - 1);
                current.setPoints(points);
            }
            this.ignoreInvalidate = false;
        }
    }



    private void unhookAll() {
        this.container.removeLayoutListener(this.listener);
        if (this.figuresToBounds != null) {
            Iterator<IFigure> figureItr = this.figuresToBounds.keySet().iterator();

            while(figureItr.hasNext()) {
                IFigure child = figureItr.next();
                figureItr.remove();
                this.removeChild(child);
            }

            this.figuresToBounds = null;
        }

    }

    public void remove(Connection connection) {
        this.staleConnections.remove(connection);
        this.constraintMap.remove(connection);
        if (this.connectionToPaths != null) {
            OrthogonalPath path = this.connectionToPaths.remove(connection);
            this.algorithm.removePath(path);
            this.isDirty = true;
            if (this.connectionToPaths.isEmpty()) {
                this.unhookAll();
                this.connectionToPaths = null;
            } else {
                this.queueRerouting();
            }

        }
    }

    void removeChild(IFigure child) {
        if (this.connectionToPath != null) {
            Rectangle bounds = child.getBounds().getCopy();
            boolean change = this.algorithm.removeObstacle(bounds);
            this.figuresToBounds.remove(child);
            child.removeFigureListener(this.figureListener);
            if (change) {
                this.isDirty = true;
                this.queueRerouting();
            }

        }
    }

    public void setSpacing(int spacing) {
        this.algorithm.setSpacing(spacing);
    }

    public boolean hasMoreConnections() {
        return this.connectionToPath != null && !this.connectionToPath.isEmpty();
    }

    public IFigure getContainer() {
        return this.container;
    }

    public void setIgnoreInvalidate(boolean b) {
        this.ignoreInvalidate = b;
    }

    public boolean shouldIgnoreInvalidate() {
        return this.ignoreInvalidate;
    }

    public boolean isDirty() {
        return this.isDirty;
    }

    public boolean containsConnection(Connection conn) {
        return this.connectionToPath != null && this.connectionToPath.containsKey(conn);
    }


    @Override
    public void setConstraint(Connection connection, Object constraint) {
        this.staleConnections.add(connection);
        this.constraintMap.put(connection, constraint);
        this.isDirty = true;
    }

    private class LayoutTracker extends Stub {
        private LayoutTracker() {
        }

        public void postLayout(IFigure container) {
            MikamiTabuchiConnectionRouter.this.processLayout();
        }

        public void remove(IFigure child) {
            MikamiTabuchiConnectionRouter.this.removeChild(child);
        }

        public void setConstraint(IFigure child, Object constraint) {
            MikamiTabuchiConnectionRouter.this.addChild(child);
        }
    }

    private void processLayout() {
        if (!this.staleConnections.isEmpty()) {
            this.staleConnections.iterator().next().revalidate();
        }
    }
}
