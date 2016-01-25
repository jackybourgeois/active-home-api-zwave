package org.activehome.api.zwave;

import org.activehome.api.API;
import org.activehome.com.Request;
import org.activehome.com.Response;
import org.activehome.com.error.*;
import org.activehome.com.error.Error;
import org.activehome.context.data.ComponentProperties;
import org.kevoree.annotation.*;
import org.kevoree.log.Log;
import org.zwave4j.*;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Zwave API - allow the system to receive and send messages
 * through a a Zwave network.
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class ZwaveAPI extends API {

    private Manager manager;
    private boolean driverReady = false;
    private long zwaveCtrlId;

    // link between zwave id and active home id
    private HashMap<Short, String> nodeIdMap;

    // <cmd, userId>
    private HashMap<String, String> cmdSenderMap;

    @Param(defaultValue = "/dev/ttyAMA0")
    private String zPort;
    @Param(defaultValue = "/var/opt/config")
    private String zConfigPath;

    @Override
    public void sendOutside(String msgStr) {
        /*if (message.getDest().startsWith(getId() + "://")) {
            //UUID id = UUID.fromString(comWrapper.getDest().substring(getId().length()+3));
            if (message instanceof Request) {
                addReqWaitingForExtResp((Request) message);
                // TODO send request outside
                // sendTo(_connections.get(id),comWrapper);
            } else if (message instanceof Response) {
                for (UUID uid : _reqWaitingForSysRespMap.keySet())
                    Log.debug(uid.toString());
                HttpServerExchange exchange = removeReqWaitingForSysResp(id);
                if (exchange!=null) {
                    Response response = (Response)comWrapper;
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
                    if (response.getErrorNum()==0) {
                        exchange.getResponseSender().send(response.getResult().toString());
                        exchange.endExchange();
                    }
                }
            } else if (message instanceof Notification) {
                Notification notification = (Notification) message;
                if (_connections.get(notification.getDest())!=null) {
                    WebSockets.sendText(notification.toString(), _connections.get(notification.getDest()), null);
                }
            }
        }*/
    }

    @Start
    public void start() {
        super.start();
        NativeLibraryLoader.loadLibrary(ZWave4j.LIBRARY_NAME, ZWave4j.class);

        cmdSenderMap = new HashMap<>();
        nodeIdMap = new HashMap<>();

        final Options options = Options.create(zConfigPath, "", "");
        options.addOptionBool("ConsoleOutput", false);
        options.lock();

        manager = Manager.create();
        manager.addWatcher(new NotificationWatcherImpl(this), null);
        manager.addDriver(zPort);
    }

    @Stop
    public void stop() {
        new Thread(() -> {
            //manager.removeWatcher(_notifWatcher, null);
            manager.removeDriver(zPort);
            Manager.destroy();
            Options.destroy();
        }).start();
    }

    @Update
    public void update() {
        stop();
        start();
    }

    public void command2Ctrl(String command) {
        if (driverReady) {
            manager.cancelControllerCommand(zwaveCtrlId);
            manager.beginControllerCommand(zwaveCtrlId, ControllerCommand.valueOf(command));
        }
    }

    public HashMap<String, String> getCmdSender() {
        return cmdSenderMap;
    }

    public Object includeMode() {
        return execute(ControllerCommand.ADD_DEVICE);
    }

    public Object excludeMode() {
        return execute(ControllerCommand.REMOVE_DEVICE);
    }

    private Object execute(ControllerCommand cc) {
        manager.cancelControllerCommand(zwaveCtrlId);
        manager.beginControllerCommand(zwaveCtrlId, cc, GENERIC_COMMAND_CALLBACK);
        TimerTask tt = new TimerTask() {

            @Override
            public void run() {
                manager.cancelControllerCommand(zwaveCtrlId);
            }
        };
        new Timer().schedule(tt, 10000);
        return "a message not really accurate!";
    }

    public Object cancelCtrlCmd() {
        if (driverReady) {
            manager.cancelControllerCommand(zwaveCtrlId);
            return "Command canceled.";
        }
        return new Error(ErrorType.NOT_FOUND, "The communication with the Zwave network is currently unavailable.");
    }

    public Object softReset() {
        if (driverReady) {
            manager.softReset(zwaveCtrlId);
            return "Command soft reset issued.";
        }
        return new Error(ErrorType.NOT_FOUND, "The communication with the Zwave network is currently unavailable.");
    }

    public Object resetController() {
        if (driverReady) {
            manager.resetController(zwaveCtrlId);
            return "The Zwave network configuration has been reset";
        }
        return new Error(ErrorType.NOT_FOUND, "The communication with the Zwave network is currently unavailable.");
    }

    public void setZwaveCtrlId(long id) {
        zwaveCtrlId = id;
        driverReady = true;
    }

    public long getZwaveCtrlId() {
        return zwaveCtrlId;
    }

    public HashMap<Short, String> getNodeIdMap() {
        return nodeIdMap;
    }

    public Manager getManager() {
        return manager;
    }

    public void setDriverReady(boolean ready) {
        driverReady = ready;
    }

    @Override
    public void onResponseReady(Request request, Response response) {
        cmdSenderMap.put(request.getMethod(), request.getSrc());
    }


    public void requestComponentStart(String compType, String id) {
        ComponentProperties cp = new ComponentProperties(compType, id);
        cp.getPortDestinationMap().put("toAPI", new LinkedList<>());
        cp.getPortDestinationMap().get("toAPI").add(getId());
        Request req = new Request(getId(), "linker", getCurrentTime(), "startComponent", new Object[]{cp});
        sendRequest(req, null);
    }

    private static final ControllerCallback GENERIC_COMMAND_CALLBACK = (cs, ce, o) -> {
        if (cs.equals(ControllerState.COMPLETED)) {
            Log.info("Successfully complete operation");
        } else if (cs.equals(ControllerState.CANCEL)) {
            Log.info("Operation canceled");
        }
    };

}
