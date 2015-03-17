/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.worldviewer;

import java.awt.BorderLayout;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.core.world.generator.facets.BiomeFacet;
import org.terasology.core.world.generator.facets.FloraFacet;
import org.terasology.core.world.generator.facets.TreeFacet;
import org.terasology.polyworld.biome.WhittakerBiomeFacet;
import org.terasology.polyworld.moisture.MoistureModelFacet;
import org.terasology.polyworld.rivers.RiverModelFacet;
import org.terasology.polyworld.voronoi.GraphFacet;
import org.terasology.world.generation.WorldFacet;
import org.terasology.world.generation.facets.base.FieldFacet2D;
import org.terasology.world.generator.WorldGenerator;
import org.terasology.worldviewer.config.Config;
import org.terasology.worldviewer.core.ConfigPanel;
import org.terasology.worldviewer.core.FacetPanel;
import org.terasology.worldviewer.core.Viewer;
import org.terasology.worldviewer.layers.CoreBiomeFacetLayer;
import org.terasology.worldviewer.layers.FacetLayer;
import org.terasology.worldviewer.layers.FieldFacetLayer;
import org.terasology.worldviewer.layers.FloraFacetLayer;
import org.terasology.worldviewer.layers.GraphFacetLayer;
import org.terasology.worldviewer.layers.MoistureModelFacetLayer;
import org.terasology.worldviewer.layers.RiverModelFacetLayer;
import org.terasology.worldviewer.layers.TreeFacetLayer;
import org.terasology.worldviewer.layers.WhittakerBiomeFacetLayer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * The main MapViewer JFrame
 * @author Martin Steiger
 */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = -8474971565041036025L;

    private static final Logger logger = LoggerFactory.getLogger(MainFrame.class);

    private final Config config;
    private final Timer memoryTimer;

    private final WorldGenerator worldGen;

    /**
     * A thread-safe list (required for parallel tile rendering)
     */
    private final List<FacetLayer> layerList;

    private final Viewer viewer;
    private final FacetPanel layerPanel;
    private final ConfigPanel configPanel;
    private final JPanel statusBar = new JPanel();

    public MainFrame(WorldGenerator worldGen, Config config) {

        this.worldGen = worldGen;
        this.config = config;

        List<FacetLayer> loadedLayers = Lists.newArrayList();

        // Fill it with default values first
        for (Class<? extends WorldFacet> facet : worldGen.getWorld().getAllFacets()) {
            loadedLayers.addAll(getLayers(facet));
        }

        // Then try to replace them with those from the config file
        try {
            loadedLayers = config.loadLayers(worldGen.getUri(), loadedLayers);
        } catch (RuntimeException e) {
            logger.warn("Could not load layers - using default", e);
        }

        // assign to thread-safe implementation
        // do it outside the try/catch clause to allow for making it final
        layerList = Lists.newCopyOnWriteArrayList(loadedLayers);

        configPanel = new ConfigPanel(worldGen, config);

        viewer = new Viewer(worldGen, layerList, config.getViewConfig());
        layerPanel = new FacetPanel(layerList);

        configPanel.addObserver(wg -> viewer.invalidateWorld());

        add(layerPanel, BorderLayout.EAST);
        add(configPanel, BorderLayout.WEST);
        add(viewer, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        JLabel memoryLabel = new JLabel();
        memoryTimer = new Timer(500, event -> {
            Runtime runtime = Runtime.getRuntime();
            long maxMem = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMem = runtime.freeMemory();
            long allocMemory = totalMemory - freeMem;
            long oneMeg = 1024 * 1024;
            memoryLabel.setText(String.format("Memory: %d/%d MB", allocMemory / oneMeg, maxMem / oneMeg));
        });
        memoryTimer.setInitialDelay(0);
        memoryTimer.start();

        statusBar.setLayout(new BoxLayout(statusBar, BoxLayout.LINE_AXIS));
        statusBar.add(memoryLabel);
        statusBar.add(Box.createHorizontalGlue());
        statusBar.add(new JLabel("Drag with right mouse button to navigate, use the wheel to zoom"));
        statusBar.setBorder(new EmptyBorder(2, 5, 2, 5));
    }

    @SuppressWarnings("unchecked")
    private static Collection<FacetLayer> getLayers(Class<? extends WorldFacet> facetClass) {

        List<FacetLayer> result = Lists.newArrayList();

        Map<Class<?>, Function<Class<?>, FacetLayer>> mapping = Maps.newLinkedHashMap();

        mapping.put(FieldFacet2D.class,
                clazz -> new FieldFacetLayer((Class<FieldFacet2D>) clazz, 0, 5));

        mapping.put(WhittakerBiomeFacet.class,
                clazz -> new WhittakerBiomeFacetLayer());

        mapping.put(BiomeFacet.class,
                clazz -> new CoreBiomeFacetLayer());

        mapping.put(MoistureModelFacet.class,
                clazz -> new MoistureModelFacetLayer());

        mapping.put(RiverModelFacet.class,
                clazz -> new RiverModelFacetLayer());

        mapping.put(GraphFacet.class,
                clazz -> new GraphFacetLayer());

        mapping.put(FloraFacet.class,
                clazz -> new FloraFacetLayer());

        mapping.put(TreeFacet.class,
                clazz -> new TreeFacetLayer());

        for (Class<?> clazz : mapping.keySet()) {
            if (clazz.isAssignableFrom(facetClass)) {
                result.add(mapping.get(clazz).apply(facetClass));
            }
        }

//        if (ObjectFacet2D.class.isAssignableFrom(facetClass)) {
//            Class<ObjectFacet2D<Object>> cast = (Class<ObjectFacet2D<Object>>) facetClass;
//            result.add(new NominalFacetLayer<Object>(cast, new RandomObjectColors()));
//        }

        if (result.isEmpty()) {
            logger.warn("No layers found for facet {}", facetClass.getName());
        }

        return result;
    }

    @Override
    public void dispose() {
        super.dispose();

        memoryTimer.stop();

        viewer.close();

        config.storeLayers(worldGen.getUri(), layerList);
    }

}
