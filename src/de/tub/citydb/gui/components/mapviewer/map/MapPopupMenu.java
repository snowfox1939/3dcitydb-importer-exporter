package de.tub.citydb.gui.components.mapviewer.map;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.jdesktop.swingx.JXMapViewer;
import org.jdesktop.swingx.mapviewer.GeoPosition;
import org.jdesktop.swingx.mapviewer.TileFactory;

import de.tub.citydb.api.event.EventDispatcher;
import de.tub.citydb.api.registry.ObjectRegistry;
import de.tub.citydb.config.Config;
import de.tub.citydb.config.internal.Internal;
import de.tub.citydb.gui.components.mapviewer.geocoder.Geocoder;
import de.tub.citydb.gui.components.mapviewer.geocoder.GeocoderResponse;
import de.tub.citydb.gui.components.mapviewer.geocoder.Location;
import de.tub.citydb.gui.components.mapviewer.geocoder.LocationType;
import de.tub.citydb.gui.components.mapviewer.geocoder.ResultType;
import de.tub.citydb.gui.components.mapviewer.geocoder.StatusCode;
import de.tub.citydb.gui.components.mapviewer.map.DefaultWaypoint.WaypointType;
import de.tub.citydb.gui.components.mapviewer.map.event.MapBoundsSelection;
import de.tub.citydb.gui.components.mapviewer.map.event.ReverseGeocoderEvent;

@SuppressWarnings("serial")
public class MapPopupMenu extends JPopupMenu {
	private final Map mapViewer;
	private final Config config;
	private final JXMapViewer map;
	private final EventDispatcher eventDispatcher;

	private JMenuItem zoomIn;
	private JMenuItem zoomOut;
	private JMenuItem centerMap;
	private JMenuItem mapBounds;
	private JMenuItem geocode;

	private Point mousePosition;

	public MapPopupMenu(Map mapViewer, Config config) {
		this.mapViewer = mapViewer;
		this.map = mapViewer.getMapKit().getMainMap();
		this.config = config;
		
		eventDispatcher = ObjectRegistry.getInstance().getEventDispatcher();
		
		init();
	}

	private void init() { 
		zoomIn = new JMenuItem("Zoom in");
		zoomOut = new JMenuItem("Zoom out");
		centerMap = new JMenuItem("Center map here");
		mapBounds = new JMenuItem("Get map bounds");
		geocode = new JMenuItem("Lookup address");

		zoomIn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				map.setZoom(map.getZoom() - 1);
			}
		});

		zoomOut.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				map.setZoom(map.getZoom() + 1);
			}
		});

		centerMap.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				map.setCenterPosition(map.convertPointToGeoPosition(mousePosition));
			}
		});

		mapBounds.addActionListener(new ActionListener() {			
			public void actionPerformed(ActionEvent e) {
				Rectangle view = map.getViewportBounds();
				TileFactory fac = map.getTileFactory();
				int zoom = map.getZoom();

				final GeoPosition[] bounds = new GeoPosition[2];
				bounds[0] = fac.pixelToGeo(new Point2D.Double(view.getMinX(), view.getMaxY()), zoom);
				bounds[1] = fac.pixelToGeo(new Point2D.Double(view.getMaxX(), view.getMinY()), zoom);
				
				eventDispatcher.triggerEvent(new MapBoundsSelection(bounds, MapPopupMenu.this));
			}
		});

		geocode.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Thread t = new Thread() {
					public void run() {
						eventDispatcher.triggerEvent(new ReverseGeocoderEvent(MapPopupMenu.this));

						GeoPosition position = map.convertPointToGeoPosition(mousePosition);
						final GeocoderResponse response = Geocoder.geocode(position, config.getProject().getGlobal().getHttpProxy());

						if (response.getStatus() == StatusCode.OK) {
							int index;

							for (index = 0; index < response.getLocations().length; ++index) {
								Location tmp = response.getLocations()[index];

								Point2D southWest = map.convertGeoPositionToPoint(tmp.getViewPort().getSouthWest());
								Rectangle2D sizeOnScreen = new Rectangle.Double(southWest.getX(), southWest.getY(), 0, 0);
								sizeOnScreen.add(map.convertGeoPositionToPoint(tmp.getViewPort().getNorthEast()));

								if (tmp.getResultTypes().contains(ResultType.POSTAL_CODE))
									continue;

								if (sizeOnScreen.getHeight() * sizeOnScreen.getWidth() >= 500)
									break;
							}

							if (index == response.getLocations().length)
								--index;

							final Location location = response.getLocations()[index];
							Set<GeoPosition> set = new HashSet<GeoPosition>(2);
							set.add(location.getPosition());
							set.add(position);
							map.calculateZoomFrom(set);

							WaypointType type = location.getLocationType() == LocationType.ROOFTOP ? 
									WaypointType.PRECISE : WaypointType.APPROXIMATE;

							mapViewer.getWaypointPainter().showWaypoints(
									new DefaultWaypoint(position, WaypointType.REVERSE),
									new DefaultWaypoint(location.getPosition(), type));
							map.repaint();

							eventDispatcher.triggerEvent(new ReverseGeocoderEvent(location, MapPopupMenu.this));

						} else {
							mapViewer.getWaypointPainter().clearWaypoints();
							map.repaint();
							
							eventDispatcher.triggerEvent(new ReverseGeocoderEvent(response, MapPopupMenu.this));
						}
					}
				};
				t.setDaemon(true);
				t.start();
			}
		});

		add(zoomIn);
		add(zoomOut);
		add(centerMap);
		addSeparator();
		add(mapBounds);
		addSeparator();
		add(geocode);
	}

	public void setMousePosition(Point mousePosition) {
		this.mousePosition = mousePosition;
	}

	protected void doTranslation() {
		zoomIn.setText(Internal.I18N.getString("map.popup.zoomIn"));
		zoomOut.setText(Internal.I18N.getString("map.popup.zoomOut"));
		centerMap.setText(Internal.I18N.getString("map.popup.centerMap"));
		mapBounds.setText(Internal.I18N.getString("map.popup.mapBounds"));
		geocode.setText(Internal.I18N.getString("map.popup.geocode"));
	}

}