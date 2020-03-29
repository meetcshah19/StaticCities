/*
 * Copyright 2015 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.staticCities.events;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.assets.management.AssetManager;
import org.terasology.context.internal.ContextImpl;
import org.terasology.entitySystem.entity.EntityBuilder;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.entity.internal.PojoEntityManager;
import org.terasology.entitySystem.entity.lifecycleEvents.OnActivatedComponent;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.prefab.PrefabManager;
import org.terasology.entitySystem.prefab.internal.PojoPrefabManager;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.characters.events.ActivationRequest;
import org.terasology.logic.characters.events.OnEnterBlockEvent;
import org.terasology.logic.characters.events.OnItemUseEvent;
import org.terasology.logic.common.ActivateEvent;
import org.terasology.logic.common.DisplayNameComponent;
import org.terasology.logic.console.Console;
import org.terasology.logic.inventory.ItemComponent;
import org.terasology.logic.inventory.events.GiveItemEvent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.Side;
import org.terasology.math.geom.Circle;
import org.terasology.math.geom.Vector2f;
import org.terasology.math.geom.Vector3f;
import org.terasology.math.geom.Vector3i;
import org.terasology.network.Client;
import org.terasology.network.NetworkComponent;
import org.terasology.network.NetworkSystem;
import org.terasology.registry.In;
import org.terasology.rendering.FontColor;
import org.terasology.rendering.assets.texture.TextureRegionAsset;
import org.terasology.staticCities.SettlementComponent;
import org.terasology.staticCities.settlements.Settlement;
import org.terasology.staticCities.sites.Site;
import org.terasology.structureTemplates.components.BlockRegionTransformComponent;
import org.terasology.structureTemplates.components.FallingBlocksPlacementAlgorithmComponent;
import org.terasology.structureTemplates.components.SpawnStructureActionComponent;
import org.terasology.structureTemplates.components.StructureTemplateComponent;
import org.terasology.structureTemplates.events.SpawnStructureEvent;
import org.terasology.structureTemplates.events.StructureBlocksSpawnedEvent;
import org.terasology.structureTemplates.internal.systems.StructureSpawnServerSystem;
import org.terasology.structureTemplates.util.BlockRegionTransform;
import org.terasology.world.WorldComponent;
import org.terasology.world.chunks.event.PurgeWorldEvent;

/**
 * Tracks player movements with respect to {@link Settlement} entities.
 */
@RegisterSystem
public class PlayerTracker extends BaseComponentSystem {

    private static final Logger logger = LoggerFactory.getLogger(PlayerTracker.class);
    @In
    private EntityManager entityManager;
    @In
    private AssetManager assetManager;
    @In
    private PrefabManager prefabManager;
    @In
    private NetworkSystem networkSystem;

    @In
    private Console console;

    private final Set<Settlement> knownSettlements = new LinkedHashSet<>();
    private final Map<String, Settlement> prevLoc = new HashMap<>();

    @ReceiveEvent(components = {SettlementComponent.class})
    public void onActivated(OnActivatedComponent event, EntityRef entity, SettlementComponent comp) {
        knownSettlements.add(comp.settlement);
    }

    /**
     * Called whenever a block is entered
     * @param event the event
     * @param entity the character entity reference "player:engine"
     */
    @ReceiveEvent
    public void onEnterBlock(OnEnterBlockEvent event, EntityRef entity) {
        LocationComponent loc = entity.getComponent(LocationComponent.class);
        Vector3f worldPos3d = loc.getWorldPosition();
        Vector2f worldPos = new Vector2f(worldPos3d.x, worldPos3d.z);

        Client client = networkSystem.getOwner(entity);

        // TODO: entity can be AI-controlled, too. These don't have an owner
        if (client == null) {
            return;
        }

        String id = client.getId();
        String name = client.getName();

        Settlement newArea = null;
        for (Settlement area : knownSettlements) {
            Site site = area.getSite();
            Circle circle = new Circle(site.getPos().x(), site.getPos().y(), site.getRadius());
            if (circle.contains(worldPos)) {
                if (newArea != null) {
                    logger.warn("{} appears to be in {} and {} at the same time!", name, newArea.getName(), area.getName());
                }

                newArea = area;
            }
        }

        if (!Objects.equals(newArea, prevLoc.get(id))) {       // both can be null
            if (newArea != null) {
                entity.send(new OnEnterSettlementEvent(newArea));






            }
            Settlement prevArea = prevLoc.put(id, newArea);
            if (prevArea != null) {
                entity.send(new OnLeaveSettlementEvent(prevArea));
            }
        }
    }

    /**
     * Called whenever a named area is entered
     * @param event the event

     * @param entity the character entity reference "player:engine"
     */
    @ReceiveEvent
    public void onEnterArea(OnEnterSettlementEvent event, EntityRef entity) {

        Client client = networkSystem.getOwner(entity);

        String playerName = String.format("%s (%s)", client.getName(), client.getId());
        String areaName = event.getSettlement().getName();

        playerName = FontColor.getColored(playerName, CitiesColors.PLAYER);
        areaName = FontColor.getColored(areaName, CitiesColors.AREA);

        console.addMessage(playerName + " entered " + areaName);


        Prefab prefab = prefabManager.getPrefab("StaticCities:well");
//        EntityBuilder entityBuilder = entityManager.newBuilder(prefab);
//        LocationComponent loc=new LocationComponent();
//        loc.setWorldPosition(client.getEntity().getComponent(LocationComponent.class).getWorldPosition());
//        entityBuilder.addComponent(loc);
//        entityBuilder.addComponent(new SpawnStructureActionComponent());
//        EntityRef entityRef = entityBuilder.build();
//
//        entityManager.create(entityRef.iterateComponents());
////        entityRef.send(new StructureBlocksSpawnedEvent(BlockRegionTransform.createFromComponent(prefab.getComponent(BlockRegionTransformComponent.class))));
        EntityBuilder entityBuilder = entityManager.newBuilder(prefab);
//        ItemComponent itemComponent = entityBuilder.getComponent(ItemComponent.class);
//        if (itemComponent == null) {
//            itemComponent = new ItemComponent();
//        }
//        Optional<TextureRegionAsset> optionalIcon = assetManager.getAsset("engine:items#whiteRecipe", TextureRegionAsset.class);
//        itemComponent.icon = optionalIcon.get();
//        itemComponent.damageType = assetManager.getAsset("engine:physicalDamage", Prefab.class).get();
//        itemComponent.pickupPrefab = assetManager.getAsset("engine:itemPickup", Prefab.class).get();
//        entityBuilder.addOrSaveComponent(itemComponent);
//        DisplayNameComponent displayNameComponent = entityBuilder.getComponent(DisplayNameComponent.class);
//        if (displayNameComponent == null) {
//            displayNameComponent = new DisplayNameComponent();
//        }

//        displayNameComponent.name =  prefab.getName() + " Spawner";
//        entityBuilder.addOrSaveComponent(displayNameComponent);
//        entityBuilder.addOrSaveComponent(new SpawnStructureActionComponent());
        // TODO make this optional
//        entityBuilder.addOrSaveComponent(new FallingBlocksPlacementAlgorithmComponent());
//        entityBuilder.addOrSaveComponent(new NetworkComponent());
        logger.debug(entityBuilder.toString());
        EntityRef item = entityBuilder.build();
        logger.debug(item.toFullDescription());
//        item.send(new ActivateEvent(new ActivationRequest()));

        LocationComponent characterLocation = client.getEntity().getComponent(LocationComponent.class);
//        Vector3f directionVector = characterLocation.getWorldDirection();
//
//        Side facedDirection = Side.inHorizontalDirection(directionVector.getX(), directionVector.getZ());
//        Side wantedFrontOfStructure = facedDirection.reverse();
//        BlockRegionTransform b = BlockRegionTransform.createRotationThenMovement(Side.TOP, Side.BOTTOM,new Vector3i(characterLocation.getWorldPosition().x+1,characterLocation.getWorldPosition().y-1,characterLocation.getWorldPosition().z+1 ));
        item.send(new SpawnStructureEvent(BlockRegionTransform.getTransformationThatDoesNothing()));
//        entity.send(onItemUseEvent);
//        giveItemToOwnerOrDestroyItem(item, entity);


    }
    void giveItemToOwnerOrDestroyItem(EntityRef item, EntityRef owner) {
        GiveItemEvent giveItemEvent = new GiveItemEvent(owner);
        item.send(giveItemEvent);
        if (!giveItemEvent.isHandled()) {
            item.destroy();
        }
    }
    /**
     * Called whenever a named area is entered
     * @param event the event
     * @param entity the character entity reference "player:engine"
     */
    @ReceiveEvent
    public void onLeaveArea(OnLeaveSettlementEvent event, EntityRef entity) {

        Client client = networkSystem.getOwner(entity);
        String playerName = String.format("%s (%s)", client.getName(), client.getId());
        String areaName = event.getSettlement().getName();

        playerName = FontColor.getColored(playerName, CitiesColors.PLAYER);
        areaName = FontColor.getColored(areaName, CitiesColors.AREA);

        console.addMessage(playerName + " left " + areaName);
    }

    @ReceiveEvent(components = {WorldComponent.class})
    public void onPurgeWorld(PurgeWorldEvent event, EntityRef worldEntity) {
        knownSettlements.clear();
        prevLoc.clear();
    }
}
