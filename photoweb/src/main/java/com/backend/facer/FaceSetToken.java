package com.backend.facer;

import com.backend.dao.GlobalConfDao;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.utils.conf.AppConfig;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class FaceSetToken {
    public static final String CURRENT_FACESETID_CONF_KEY = "facesetkeyid";

    public static final String FACESET_ID_PREFIX = AppConfig.getInstance().getFaceSetPrefix();

    private static final Logger logger = LoggerFactory.getLogger(FaceSetToken.class);

    private int currentFaceSetID = -1;

    private static FaceSetToken instance = new FaceSetToken();

    private int facecount = -1;

    private volatile boolean isInit = false;

    private FaceSetToken() {

    }

    public static FaceSetToken getInstance() {
        instance.init();
        return instance;
    }

    public synchronized void init() {
        if (isInit) {
            return;
        }

        try {
            if (currentFaceSetID < 0) {
                String cid = getLastSNFromConfTable();
                if (!StringUtils.isBlank(cid)) {
                    try {
                        currentFaceSetID = Integer.parseInt(cid);
                    } catch (Exception e) {
                        logger.warn("fomat error: ", e);
                    }
                } else {
                    currentFaceSetID = 0;
                    GlobalConfDao.getInstance()
                            .setConf(CURRENT_FACESETID_CONF_KEY, currentFaceSetID + "");
                }
                createFaceSet();
            }

            if (facecount < 0) {
                facecount = getFaceCount();
                logger.warn("the count of faces in the faceset[{}] is {}", getCurrentFaceSetID(),
                        facecount);
            }

            while (facecount >= 3000) {
                refreshFaceCount();
            }

            logger.warn("init successfully, the current faceset is " + getCurrentFaceSetID());

            isInit = true;
        } catch (Exception e) {
            logger.warn("caused by: ", e);
            // TODO need sleep a time.
        }
    }

    private void refreshFaceCount() {
        currentFaceSetID++;
        createFaceSet();
        GlobalConfDao.getInstance().setConf(CURRENT_FACESETID_CONF_KEY, currentFaceSetID + "");
        facecount = getFaceCount();
        logger.warn("the faceset id is {}, facecount is {}", currentFaceSetID, facecount);
    }

    public synchronized String acquireFaceSetID() {
        while (facecount >= 3000) {
            refreshFaceCount();
        }

        facecount++;
        return getCurrentFaceSetID();
    }

    /*
     * ?????? api_key String ?????????API???API Key ?????? api_secret String ?????????API???API Secret
     * ?????? display_name String ??????????????????????????????256??????????????????????????????^@,&=*'" ?????? outer_id String
     * ????????????????????????FaceSet????????????????????????????????????FaceSet???????????????255??????????????????????????????^@,&=*'" ?????? tags
     * String
     * FaceSet?????????????????????????????????????????????FaceSet???????????????255??????????????????tag????????????????????????tag??????????????????^@,&=*'"
     * ?????? face_tokens String ????????????face_token??????????????????????????????????????????????????????????????????5???face_token ??????
     * user_data String ?????????????????????????????????16KB?????????????????????^@,&=*'" ?????? force_merge Int
     * ?????????outer_id?????????????????????outer_id????????????????????????face_token?????????????????????FaceSet???
     * 0?????????face_tokens??????????????????FaceSet??????????????????FACESET_EXIST??????
     * 1??????face_tokens??????????????????FaceSet??? ????????????0
     */
    private void createFaceSet() {
        String facesetid = getCurrentFaceSetID();
        Map<String, Object> mp = new HashMap<>();
        mp.put("display_name", "jAlbum_FaceSet");
        mp.put("outer_id", facesetid);
        String result = FacerUtils.post(FacerUtils.FACESET_CREATE_URL, mp);
        if (StringUtils.isBlank(result)) {
            logger.warn("create faceset failed: " + facesetid);
        } else {
            logger.warn("created the faceset: {}", result);
            GlobalConfDao.getInstance().setConf(CURRENT_FACESETID_CONF_KEY, currentFaceSetID + "");
        }
    }

    private int getFaceCount() {
        String faceSetID = getCurrentFaceSetID();
        Map<String, Object> mp = new HashMap<>();
        mp.put(FacerUtils.OUTER_ID, faceSetID);
        String result = FacerUtils.post(FacerUtils.FACESET_DETAIL_URL, mp);
        if (StringUtils.isBlank(result)) {
            return 0;
        }

        JsonParser parser = new JsonParser();
        JsonObject jr = (JsonObject) parser.parse(result);
        return jr.get(FacerUtils.FACE_COUNT).getAsInt();
    }

    public List<String> getFaceTokens(String faceSetID) {
        if (StringUtils.isBlank(faceSetID)) {
            return null;
        }

        Map<String, Object> mp = new HashMap<>();
        mp.put(FacerUtils.OUTER_ID, faceSetID);
        String start = "";
        List<String> flst = new LinkedList<>();
        while (true) {
            if (StringUtils.isNotBlank(start)) {
                mp.put(FacerUtils.START, start);
            }

            String result = FacerUtils.post(FacerUtils.FACESET_DETAIL_URL, mp);
            if (StringUtils.isBlank(result)) {
                break;
            }

            JsonParser parser = new JsonParser();
            JsonObject jr = (JsonObject) parser.parse(result);
            JsonArray ja = jr.getAsJsonArray("face_tokens");
            if (ja != null) {
                for (int i = 0; i != ja.size(); i++) {
                    flst.add(ja.get(i).getAsString());
                }
            }

            JsonElement next = jr.get("next");
            if (next == null) {
                break;
            } else {
                start = next.getAsString();
            }
        }

        return flst;
    }

    private String getLastSNFromConfTable() {
        return GlobalConfDao.getInstance().getConf(CURRENT_FACESETID_CONF_KEY);
    }

    public String getCurrentSN() {
        if (currentFaceSetID == -1) {
            return getLastSNFromConfTable();
        }

        return currentFaceSetID + "";
    }

    private String getCurrentFaceSetID() {
        return getFaceSetIDBySn(currentFaceSetID);
    }

    public String getFaceSetIDBySn(int sn) {
        return FACESET_ID_PREFIX + sn;
    }
}
