package com.baidu.monitoring.controller;

import com.alibaba.fastjson.JSONObject;
import com.baidu.monitoring.util.R;
import com.baidu.monitoring.webscoket.StatusChangeWebsocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import static com.baidu.monitoring.util.SessionManager.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
public class TwinBaseController {

    @Autowired
    private StatusChangeWebsocketHandler statusChangeWebsocketHandler;
//
//    @GetMapping("/updateMaxLinkNum")
//    public R updateClientNum (@RequestParam("maxNum") Integer maxNum , @RequestParam("serverIp") String serverIp){
//        return statusChangeWebsocketHandler.updateServerLink(maxNum,serverIp);
//    }
//
    /**
     * ue接口转发
     * @param input
     * @return
     */
    @PostMapping("/project/publish")
    public R ueForward (@RequestBody JSONObject input){
        return statusChangeWebsocketHandler.ueForward(input);
    }

    /**
     * 获取当前服务状态
     * @return
     */
    @GetMapping("/serverStatus")
    public R serverStatus (){
        return R.ok();
    }
    /**
     * UE启动成功 回调改变状态
     * @param port
     * @return
     */
    @GetMapping("/changeUeStatus")
    private R changeUeStatus(@RequestParam("port") String port) throws IOException {
        statusChangeWebsocketHandler.changeUeStatus(port);
        return R.ok();
    }

    /**
     * 获取当前缓存的信息
     * @return
     */
    @GetMapping("/getInfo")
    private R getInfo(){
        Map<String, Object> result = new HashMap<>();
        result.put("serverLinkInfo", serverLinkInfo);
        result.put("portPidInfo", portPidInfo);
        result.put("useLinkInfo", useLinkInfo);
        result.put("useIpInfo", useIpInfo);
        result.put("queueLinkInfo", queueLinkInfo);
        result.put("postSet", postSet);
        return R.ok(result);
    }

//    /**
//     * 获取当前GPU信息
//     * @return
//     */
//    @GetMapping("/getGpuStatus")
//    private R getGpuStatus(){
//        return R.ok(statusChangeWebsocketHandler.getGpuStatus());
//    }
}
