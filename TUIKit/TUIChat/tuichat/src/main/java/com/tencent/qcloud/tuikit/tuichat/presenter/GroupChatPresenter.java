package com.tencent.qcloud.tuikit.tuichat.presenter;

import android.text.TextUtils;
import android.util.Pair;

import com.google.gson.Gson;
import com.tencent.qcloud.tuicore.TUILogin;
import com.tencent.qcloud.tuicore.component.interfaces.IUIKitCallback;
import com.tencent.qcloud.tuikit.tuichat.R;
import com.tencent.qcloud.tuikit.tuichat.TUIChatConstants;
import com.tencent.qcloud.tuikit.tuichat.TUIChatService;
import com.tencent.qcloud.tuikit.tuichat.bean.GroupApplyInfo;
import com.tencent.qcloud.tuikit.tuichat.bean.GroupMemberInfo;
import com.tencent.qcloud.tuikit.tuichat.bean.MessageCustom;
import com.tencent.qcloud.tuikit.tuichat.bean.message.TUIMessageBean;
import com.tencent.qcloud.tuikit.tuichat.bean.message.TipsMessageBean;
import com.tencent.qcloud.tuikit.tuichat.interfaces.GroupChatEventListener;
import com.tencent.qcloud.tuikit.tuichat.bean.ChatInfo;
import com.tencent.qcloud.tuikit.tuichat.bean.GroupInfo;
import com.tencent.qcloud.tuikit.tuichat.util.TUIChatLog;
import com.tencent.qcloud.tuikit.tuichat.util.TUIChatUtils;

import java.util.ArrayList;
import java.util.List;

public class GroupChatPresenter extends ChatPresenter {
    private static final String TAG = GroupChatPresenter.class.getSimpleName();

    private GroupInfo groupInfo;
    private List<GroupApplyInfo> currentApplies = new ArrayList<>();
    private List<GroupMemberInfo> currentGroupMembers = new ArrayList<>();

    private GroupChatEventListener groupChatEventListener;

    public GroupChatPresenter() {
        super();
        TUIChatLog.i(TAG, "GroupChatPresenter Init");
    }

    public void initListener() {
        groupChatEventListener = new GroupChatEventListener() {

            @Override
            public void onGroupForceExit(String groupId) {
                GroupChatPresenter.this.onGroupForceExit(groupId);
            }

            @Override
            public void exitGroupChat(String chatId) {
                GroupChatPresenter.this.onExitChat(chatId);
            }

            @Override
            public void clearGroupMessage(String chatId) {
                if (TextUtils.equals(chatId, groupInfo.getId())) {
                    GroupChatPresenter.this.clearMessage();
                }
            }

            @Override
            public void onApplied(int unHandledSize) {
                GroupChatPresenter.this.onApplied(unHandledSize);
            }

            @Override
            public void handleRevoke(String msgId) {
                GroupChatPresenter.this.handleRevoke(msgId);
            }

            @Override
            public void onRecvNewMessage(TUIMessageBean message) {
                if (groupInfo == null || !TextUtils.equals(message.getGroupId(), groupInfo.getId())) {
                    TUIChatLog.i(TAG, "receive a new message , not belong to current chat.");
                } else {
                    GroupChatPresenter.this.onRecvNewMessage(message);
                }
            }

            @Override
            public void onGroupNameChanged(String groupId, String newName) {
                if (groupInfo == null || !TextUtils.equals(groupId, groupInfo.getId())) {
                    return;
                }
                GroupChatPresenter.this.onGroupNameChanged(newName);
            }
        };
        TUIChatService.getInstance().setGroupChatEventListener(groupChatEventListener);
        initMessageSender();
    }

    @Override
    public void loadMessage(int type, TUIMessageBean lastMessageInfo, IUIKitCallback<List<TUIMessageBean>> callback) {
        if (groupInfo == null || isLoading) {
            return;
        }
        isLoading = true;
        String chatId = groupInfo.getId();
        if (type == TUIChatConstants.GET_MESSAGE_FORWARD) {
            provider.loadGroupMessage(chatId, MSG_PAGE_COUNT, lastMessageInfo, new IUIKitCallback<List<TUIMessageBean>>() {

                @Override
                public void onSuccess(List<TUIMessageBean> data) {
                    TUIChatLog.i(TAG, "load group message success " + data.size());
                    if (lastMessageInfo == null) {
                        isHaveMoreNewMessage = false;
                    }
                    onMessageLoadCompleted(data, type);
                    TUIChatUtils.callbackOnSuccess(callback, data);
                }

                @Override
                public void onError(String module, int errCode, String errMsg) {
                    TUIChatLog.e(TAG, "load group message failed " + errCode + "  " + errMsg);
                    TUIChatUtils.callbackOnError(callback, errCode, errMsg);
                }
            });
        } else { // 向后拉更新的消息 或者 前后同时拉消息
            loadHistoryMessageList(chatId, true, type, MSG_PAGE_COUNT, lastMessageInfo, callback);
        }
    }

    // 加载消息成功之后会调用此方法
    @Override
    protected void onMessageLoadCompleted(List<TUIMessageBean> data, int getType) {
        groupReadReport(groupInfo.getId());
        processLoadedMessage(data, getType);
    }

    public void createGroupChat(final GroupInfo chatInfo, final IUIKitCallback<String> callBack) {

        provider.createGroup(chatInfo, new IUIKitCallback<String>() {
            @Override
            public void onSuccess(String groupId) {
                chatInfo.setId(groupId);
                Gson gson = new Gson();
                MessageCustom messageCustom = new MessageCustom();
                messageCustom.version = TUIChatConstants.version;
                messageCustom.businessID = MessageCustom.BUSINESS_ID_GROUP_CREATE;
                messageCustom.opUser = TUILogin.getLoginUser();
                messageCustom.content = TUIChatService.getAppContext().getString(R.string.create_group);
                String data = gson.toJson(messageCustom);


                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                sendGroupTipsMessage(groupId, data, new IUIKitCallback<String>() {
                    @Override
                    public void onSuccess(String data) {
                        TUIChatUtils.callbackOnSuccess(callBack, data);
                    }

                    @Override
                    public void onError(String module, int errCode, String errMsg) {
                        TUIChatUtils.callbackOnError(callBack, module, errCode, errMsg);
                    }
                });
            }

            @Override
            public void onError(String module, int errCode, String errMsg) {
                TUIChatUtils.callbackOnError(callBack, module, errCode, errMsg);
            }
        });
    }

    private void sendGroupTipsMessage(String groupId, String message, final IUIKitCallback<String> callBack) {
        provider.sendGroupTipsMessage(groupId, message, new IUIKitCallback<TUIMessageBean>() {
            @Override
            public void onSuccess(TUIMessageBean data) {
                TUIChatUtils.callbackOnSuccess(callBack, groupId);
            }

            @Override
            public void onError(String module, int errCode, String errMsg) {
                TUIChatUtils.callbackOnError(callBack, module, errCode, errMsg);
            }
        });
    }

    protected void addMessageInfo(TUIMessageBean messageInfo) {
        super.addMessageInfo(messageInfo);
        addGroupMessage(messageInfo);
    }


    private void addGroupMessage(TUIMessageBean message) {
        if (!(message instanceof TipsMessageBean)) {
            return;
        }
        TipsMessageBean tipsMessage = (TipsMessageBean) message;
        if (tipsMessage.getTipType() == TipsMessageBean.MSG_TYPE_GROUP_JOIN) {
            provider.addJoinGroupMessage(tipsMessage, new IUIKitCallback<List<GroupMemberInfo>>() {
                @Override
                public void onSuccess(List<GroupMemberInfo> data) {
                    currentGroupMembers.addAll(data);
                    groupInfo.setMemberDetails(currentGroupMembers);
                }

                @Override
                public void onError(String module, int errCode, String errMsg) {
                    TUIChatLog.e(TAG, "addJoinGroupMessage error : " + errMsg);
                }
            });

        } else if (tipsMessage.getTipType() == TipsMessageBean.MSG_TYPE_GROUP_QUITE || tipsMessage.getTipType() == TipsMessageBean.MSG_TYPE_GROUP_KICK) {
            provider.addLeaveGroupMessage(tipsMessage, new IUIKitCallback<List<String>>() {
                @Override
                public void onSuccess(List<String> data) {
                    for (String memberUserId : data) {
                        for (int i = 0; i < currentGroupMembers.size(); i++) {
                            if (currentGroupMembers.get(i).getAccount().equals(memberUserId)) {
                                currentGroupMembers.remove(i);
                                break;
                            }
                        }
                    }
                    groupInfo.setMemberDetails(currentGroupMembers);
                }

                @Override
                public void onError(String module, int errCode, String errMsg) {

                }
            });
        } else if (tipsMessage.getTipType() == TipsMessageBean.MSG_TYPE_GROUP_MODIFY_NAME || tipsMessage.getTipType() == TipsMessageBean.MSG_TYPE_GROUP_MODIFY_NOTICE) {
            provider.addModifyGroupMessage(tipsMessage, new IUIKitCallback<Pair<Integer, String>>() {
                @Override
                public void onSuccess(Pair<Integer, String> data) {
                    if (data.first == TipsMessageBean.MSG_TYPE_GROUP_MODIFY_NAME) {
                        groupInfo.setGroupName(data.second);
                        if (chatNotifyHandler != null) {
                            chatNotifyHandler.onGroupNameChanged(data.second);
                        }
                    }
                    if (data.first == TipsMessageBean.MSG_TYPE_GROUP_MODIFY_NOTICE) {
                        groupInfo.setNotice(data.second);
                    }
                }

                @Override
                public void onError(String module, int errCode, String errMsg) {
                    TUIChatLog.e(TAG, "addModifyGroupMessage error " + errMsg);
                }
            });
        }
    }

    protected void assembleGroupMessage(TUIMessageBean message) {
        message.setGroup(true);
    }

    public void onGroupForceExit(String groupId) {
        if (chatNotifyHandler != null && TextUtils.equals(groupId, groupInfo.getId())) {
            chatNotifyHandler.onGroupForceExit();
        }
    }

    public void onApplied(int unHandledSize) {
        if (chatNotifyHandler != null) {
            chatNotifyHandler.onApplied(unHandledSize);
        }
    }

    public void onGroupNameChanged(String newName) {
        if (chatNotifyHandler != null) {
            chatNotifyHandler.onGroupNameChanged(newName);
        }
    }

    @Override
    public ChatInfo getChatInfo() {
        return groupInfo;
    }

    public void setGroupInfo(GroupInfo groupInfo) {
        this.groupInfo = groupInfo;
    }
}
