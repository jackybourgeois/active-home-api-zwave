package org.activehome.api.zwave;

import org.activehome.com.Notif;
import org.activehome.context.data.DataPoint;
import org.kevoree.log.Log;
import org.zwave4j.Manager;
import org.zwave4j.Notification;
import org.zwave4j.NotificationWatcher;
import org.zwave4j.ValueId;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
class NotificationWatcherImpl implements NotificationWatcher {

    private final ZwaveAPI zAPI;

    public NotificationWatcherImpl(ZwaveAPI zAPI) {
        this.zAPI = zAPI;
    }

    @Override
    public void onNotification(Notification notif, Object o) {
        //long ts = zAPI.getCurrentTime();
        //zAPI.sendNotifToSys(new org.activehome.core.msg.Notification("", "", zAPI.getCurrentTime(), new DataPoint(_path, ts, notification.toString())));
        switch (notif.getType()) {
            case DRIVER_READY:
                Log.info("Driver ready");
                zAPI.setZwaveCtrlId(notif.getHomeId());
                break;
            case DRIVER_FAILED:
                Log.info("Driver failed");
                break;
            case DRIVER_RESET:
                Log.info("Driver reset");
                break;
            case AWAKE_NODES_QUERIED:
                Log.info("Awake nodes queried");
                break;
            case ALL_NODES_QUERIED:
                Log.info("All nodes queried");
                zAPI.getManager().writeConfig(zAPI.getZwaveCtrlId());
                zAPI.setDriverReady(true);
                break;
            case ALL_NODES_QUERIED_SOME_DEAD:
                Log.info("All nodes queried some dead");
                zAPI.getManager().writeConfig(zAPI.getZwaveCtrlId());
                zAPI.setDriverReady(true);
                break;
            case POLLING_ENABLED:
                Log.info("Polling enabled");
                break;
            case POLLING_DISABLED:
                Log.info("Polling disabled");
                break;
            case NODE_NEW:
                Log.info(String.format("Node new, id: %d", notif.getNodeId()));
                Log.info("=> type: " + zAPI.getManager().getNodeType(notif.getHomeId(), notif.getNodeId()));
                //zAPI.sendRequestToSys(new Question(zAPI.getId(),zAPI.getCmdSender().get(ControllerCommand.ADD_DEVICE),
                //        zAPI.getCurrentTime(), ""));
                break;
            case NODE_ADDED:
                Log.info("Node added: " + notif.getNodeId());
                if (zAPI.getNodeIdMap().containsKey(notif.getNodeId())) {
                    String id = zAPI.getNodeIdMap().get(notif.getNodeId());
                    zAPI.checkComponent(id);
                }
                break;
            case NODE_REMOVED:
                Log.info(String.format("Node removed, id: %d", notif.getNodeId()));
                break;
            case ESSENTIAL_NODE_QUERIES_COMPLETE:
                Log.info(String.format("Node essential queries complete, id: %d", notif.getNodeId()));
                break;
            case NODE_QUERIES_COMPLETE:
                Log.info(String.format("Node queries complete, id: %d", notif.getNodeId()));
                break;
            case NODE_EVENT:
                Log.info(String.format("Node event, id: %d - event id: %d", notif.getNodeId(), notif.getEvent()));
                break;
            case NODE_NAMING:
                Log.info(String.format("Node naming,  id: %d", notif.getNodeId() ));
                break;
            case NODE_PROTOCOL_INFO:
                String type = zAPI.getManager().getNodeType(notif.getHomeId(), notif.getNodeId());
                String compId = zAPI.getZwaveCtrlId() + "_" + notif.getNodeId();
                Log.info(String.format("Node protocol info,  id: %d - type: %s", notif.getNodeId(), type));
                Object comp = zAPI.checkComponent(compId);
                if (comp==null) {
                    // no component exists for this node, send a request to linker
                    String className = type2ClassName(type);
                    if (className!=null) zAPI.requestComponentStart(type2ClassName(type), compId);
                }
                break;
            case VALUE_ADDED:
                Log.info(String.format("Value added: node id: %d, command class: %d, " +
                                "instance: %d, index: %d, genre: %s, type: %s, label: %s",
                        notif.getNodeId(),
                        notif.getValueId().getCommandClassId(),
                        notif.getValueId().getInstance(),
                        notif.getValueId().getIndex(),
                        notif.getValueId().getGenre().name(),
                        notif.getValueId().getType().name(),
                        zAPI.getManager().getValueLabel(notif.getValueId())));
                sendNotification(notif.getValueId());
                break;
            case VALUE_REMOVED:
                Log.info(String.format("Value removed, node id: %d, command class: %d, instance: %d, index: %d",
                        notif.getNodeId(),
                        notif.getValueId().getCommandClassId(),
                        notif.getValueId().getInstance(),
                        notif.getValueId().getIndex()
                ));
                break;
            case VALUE_CHANGED:
                Log.info(String.format("Value changed, node id: %d, command class: %d, instance: %d, index: %d",
                        notif.getNodeId(),
                        notif.getValueId().getCommandClassId(),
                        notif.getValueId().getInstance(),
                        notif.getValueId().getIndex()));
                sendNotification(notif.getValueId());
                break;
            case VALUE_REFRESHED:
                Log.info(String.format("Value refreshed, node id: %d, command class: %d, instance: %d, " +
                                "index: %d",
                        notif.getNodeId(),
                        notif.getValueId().getCommandClassId(),
                        notif.getValueId().getInstance(),
                        notif.getValueId().getIndex()));
                sendNotification(notif.getValueId());
                break;
            case GROUP:
                Log.info(String.format("Group, node id: %d, group id: %d", notif.getNodeId(), notif.getGroupIdx()));
                break;
            case SCENE_EVENT:
                Log.info(String.format("Scene event, scene id: %d", notif.getSceneId() ));
                break;
            case CREATE_BUTTON:
                Log.info(String.format("Button create, button id: %d", notif.getButtonId() ));
                break;
            case DELETE_BUTTON:
                Log.info(String.format("Button delete, button id: %d", notif.getButtonId() ));
                break;
            case BUTTON_ON:
                Log.info(String.format("Button on, button id: %d", notif.getButtonId() ));
                break;
            case BUTTON_OFF:
                Log.info(String.format("Button off, button id: %d", notif.getButtonId() ));
                break;
            case NOTIFICATION: Log.info("Notification: " + notif.getValueId());
                break;
            default:
                Log.info(notif.getType().name());
                break;
        }
    }

    private void sendNotification(ValueId valueId) {
        String dest = zAPI.getZwaveCtrlId() + "_" + valueId.getNodeId();
        DataPoint dp = createDataPoint(valueId);
        Log.info("send dp: " + dp);
        zAPI.sendNotifToSys(new Notif(
                zAPI.getId() + "://" + dest,  dest, zAPI.getCurrentTime(), dp));
    }


    private DataPoint createDataPoint(ValueId valueId) {
        long ts = zAPI.getCurrentTime();
        String metricId = zAPI.getManager().getValueLabel(valueId);

        switch (valueId.getType()) {
            case BOOL:
                AtomicReference<Boolean> b = new AtomicReference<>();
                Manager.get().getValueAsBool(valueId, b);
                return new DataPoint(metricId,ts, b.toString());
            case BYTE:
                AtomicReference<Short> bb = new AtomicReference<>();
                Manager.get().getValueAsByte(valueId, bb);
                return new DataPoint(metricId,ts,bb.toString());
            case DECIMAL:
                AtomicReference<Float> f = new AtomicReference<>();
                Manager.get().getValueAsFloat(valueId, f);
                return new DataPoint(metricId,ts,f.toString());
            case INT:
                AtomicReference<Integer> i = new AtomicReference<>();
                Manager.get().getValueAsInt(valueId, i);
                return new DataPoint(metricId,ts,i.toString());
            case LIST:
                return null;
            case SCHEDULE:
                return null;
            case SHORT:
                AtomicReference<Short> s = new AtomicReference<>();
                Manager.get().getValueAsShort(valueId, s);
                return new DataPoint(metricId,ts,s.toString());
            case STRING:
                AtomicReference<String> ss = new AtomicReference<>();
                Manager.get().getValueAsString(valueId, ss);
                return new DataPoint(metricId,ts,ss.toString());
            case BUTTON:
                return null;
            /*case RAW:
                AtomicReference<short[]> sss = new AtomicReference<>();
                Manager.get().getValueAsRaw(valueId, sss);
                return new DataPoint(metricId,ts,sss.get());*/
            default:
                return null;
        }
    }

    private String type2ClassName(String type) {
        // TODO: should be dynalic, not a switch case... of course!
        switch (type) {
            case "Simple Meter": return "org.activehome.api.Meter";
        }
        return null;
    }

}
