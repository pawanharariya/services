/*
 * Copyright (C) 2015 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.services.sync.actions.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.fasterxml.jackson.core.type.TypeReference;

import org.opendatakit.consts.IntentConsts;
import org.opendatakit.database.RoleConsts;
import org.opendatakit.logging.WebLogger;
import org.opendatakit.properties.CommonToolProperties;
import org.opendatakit.properties.PropertiesSingleton;
import org.opendatakit.services.R;
import org.opendatakit.services.preferences.activities.IOdkAppPropertiesActivity;
import org.opendatakit.services.sync.actions.SyncActions;
import org.opendatakit.services.sync.actions.activities.DoSyncActionCallback;
import org.opendatakit.services.sync.actions.activities.ISyncServiceInterfaceActivity;
import org.opendatakit.services.sync.actions.activities.LoginActivity;
import org.opendatakit.services.sync.actions.activities.SyncActivity;
import org.opendatakit.services.utilities.Constants;
import org.opendatakit.sync.service.IOdkSyncServiceInterface;
import org.opendatakit.sync.service.SyncAttachmentState;
import org.opendatakit.sync.service.SyncOverallResult;
import org.opendatakit.sync.service.SyncProgressEvent;
import org.opendatakit.sync.service.SyncProgressState;
import org.opendatakit.sync.service.SyncStatus;
import org.opendatakit.utilities.ODKFileUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;

import java.util.Date;



/**
 * @author mitchellsundt@gmail.com
 */
public class SyncFragment extends AbsSyncUIFragment {

  private static final String TAG = "SyncFragment";

  public static final String NAME = "SyncFragment";
  public static final int ID = R.layout.sync_launch_fragment;

  private static final String SYNC_ATTACHMENT_TREATMENT = "syncAttachmentState";

  private static final String SYNC_ACTION = "syncAction";

  private static final String PROGRESS_DIALOG_TAG = "progressDialogSync";
  private static final String OUTCOME_DIALOG_TAG = "outcomeDialogSync";

  private boolean loggingIn = false;

  private LinearLayout infoPane;

  private Spinner syncInstanceAttachmentsSpinner;

  private Button startSync;
  private Button resetServer;
  private Button changeUser;

  private LinearLayout resetButtonPane;

  private SyncAttachmentState syncAttachmentState;
  private SyncActions syncAction = SyncActions.IDLE;
  private PropertiesSingleton properties;

  private TextView lastSyncField;

  public SyncFragment() {
    super(OUTCOME_DIALOG_TAG, PROGRESS_DIALOG_TAG);
  }

  @Override public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(SYNC_ATTACHMENT_TREATMENT, syncAttachmentState.name());
    outState.putString(SYNC_ACTION, syncAction.name());
  }

  @Override public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    Intent incomingIntent = getActivity().getIntent();
    if (savedInstanceState != null && savedInstanceState.containsKey(SYNC_ATTACHMENT_TREATMENT)) {
      String treatment = savedInstanceState.getString(SYNC_ATTACHMENT_TREATMENT);
      try {
        syncAttachmentState = SyncAttachmentState.valueOf(treatment);
      } catch (IllegalArgumentException e) {
        syncAttachmentState = SyncAttachmentState.SYNC;
      }
    }

    if (savedInstanceState != null && savedInstanceState.containsKey(SYNC_ACTION)) {
      String action = savedInstanceState.getString(SYNC_ACTION);
      try {
        syncAction = SyncActions.valueOf(action);
      } catch (IllegalArgumentException e) {
        syncAction = SyncActions.IDLE;
      }
    }
    disableButtons();
  }

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    super.onCreateView(inflater, container, savedInstanceState);

    View view = inflater.inflate(ID, container, false);

    infoPane = view.findViewById(R.id.sync_info_pane);
    populateTextViewMemberVariablesReferences(view);

    syncInstanceAttachmentsSpinner = view.findViewById(R.id.sync_instance_attachments);
    lastSyncField = view.findViewById(R.id.last_sync_field);


    properties = CommonToolProperties.get(this.getContext(),getAppName());
    displayLastSyncInfo();
    if(properties.containsKey(CommonToolProperties.KEY_SYNC_ATTACHMENT_STATE) && properties.getProperty(CommonToolProperties.KEY_SYNC_ATTACHMENT_STATE) != null){
      String state = properties.getProperty(CommonToolProperties.KEY_SYNC_ATTACHMENT_STATE);
      try {
        syncAttachmentState = SyncAttachmentState.valueOf(state);
      } catch (IllegalArgumentException e) {
        syncAttachmentState = SyncAttachmentState.SYNC;
      }
    } else syncAttachmentState = SyncAttachmentState.SYNC;

    if (savedInstanceState != null && savedInstanceState.containsKey(SYNC_ATTACHMENT_TREATMENT)) {
      String treatment = savedInstanceState.getString(SYNC_ATTACHMENT_TREATMENT);
      try {
        syncAttachmentState = SyncAttachmentState.valueOf(treatment);
      } catch (IllegalArgumentException e) {
        syncAttachmentState = SyncAttachmentState.SYNC;
      }
    }

    if (savedInstanceState != null && savedInstanceState.containsKey(SYNC_ACTION)) {
      String action = savedInstanceState.getString(SYNC_ACTION);
      try {
        syncAction = SyncActions.valueOf(action);
      } catch (IllegalArgumentException e) {
        syncAction = SyncActions.IDLE;
      }
    }

    ArrayAdapter<CharSequence> instanceAttachmentsAdapter = ArrayAdapter
        .createFromResource(getActivity(), R.array.sync_attachment_option_names, android.R.layout.select_dialog_item);
    syncInstanceAttachmentsSpinner.setAdapter(instanceAttachmentsAdapter);

    syncInstanceAttachmentsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String[] syncAttachmentType = getResources().getStringArray(R.array.sync_attachment_option_values);
        syncAttachmentState = SyncAttachmentState.valueOf(syncAttachmentType[position]);
        properties.setProperties(Collections.singletonMap(CommonToolProperties.KEY_SYNC_ATTACHMENT_STATE,syncAttachmentState.name()));
      }

      @Override public void onNothingSelected(AdapterView<?> parent) {
        syncAttachmentState = SyncAttachmentState.SYNC;
        properties.setProperties(Collections.singletonMap(CommonToolProperties.KEY_SYNC_ATTACHMENT_STATE,syncAttachmentState.name()));
        syncInstanceAttachmentsSpinner.setSelection(getSyncAttachmentStateIndex());
      }
    });

    startSync = view.findViewById(R.id.sync_start_button);
    startSync.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        onClickSyncNow(v);
      }
    });
    resetServer = view.findViewById(R.id.sync_reset_server_button);
    resetServer.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        onClickResetServer(v);
      }
    });
    changeUser = view.findViewById(R.id.change_user_button);
    changeUser.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        onClickChangeUser(v);
      }
    });

    resetButtonPane = view.findViewById(R.id.sync_reset_button_pane);

    return view;
  }

  @Override public void onResume() {
    super.onResume();
    syncInstanceAttachmentsSpinner.setSelection(getSyncAttachmentStateIndex());
  }

  private void disableButtons() {
    startSync.setEnabled(false);
    resetServer.setEnabled(false);
  }

  void perhapsEnableButtons() {
    PropertiesSingleton props = ((IOdkAppPropertiesActivity) this.getActivity()).getProps();
    boolean isTablesAdmin;
    {
      String rolesList = props.getProperty(CommonToolProperties.KEY_ROLES_LIST);
      // figure out whether we have a privileged user or not
      ArrayList<String> rolesArray = null;
      if (rolesList != null && rolesList.length() != 0) {
        try {
          TypeReference<ArrayList<String>> arrayListTypeReference;
          arrayListTypeReference = new TypeReference<ArrayList<String>>() {
          };
          rolesArray = ODKFileUtils.mapper.readValue(rolesList, arrayListTypeReference);
        } catch (IOException e) {
          throw new IllegalStateException("this should never happen");
        }
      }
      isTablesAdmin = (rolesArray != null) && rolesArray.contains(RoleConsts.ROLE_ADMINISTRATOR);
    }

    String url = props.getProperty(CommonToolProperties.KEY_SYNC_SERVER_URL);
    if (url == null || url.length() == 0) {
      disableButtons();
    } else {
      startSync.setEnabled(true);
      resetServer.setEnabled(isTablesAdmin);
    }

    // only show information screens if we are the tables admin
    int visibility = isTablesAdmin ? View.VISIBLE : View.GONE;
    infoPane.setVisibility(View.VISIBLE); // TODO
    resetButtonPane.setVisibility(visibility);
  }

  AlertDialog.Builder buildOkMessage(String title, String message) {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setCancelable(false);
    builder.setPositiveButton(getString(R.string.ok), null);
    builder.setTitle(title);
    builder.setMessage(message);
    return builder;
  }

  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == SyncActivity.AUTHORIZE_ACCOUNT_RESULT_CODE) {
      if (resultCode == Activity.RESULT_CANCELED) {
        syncAction = SyncActions.IDLE;
      }
      postTaskToAccessSyncService();
    }
  }

  void postTaskToAccessSyncService() {
    WebLogger.getLogger(getAppName()).w(TAG, "[" + getId() + "] [postTaskToAccessSyncService] started");
    Activity activity = getActivity();
    if (activity == null || !hasDialogBeenCreated() || !this.isResumed()) {
      // we are in transition -- do nothing
      if(activity == null) {
        WebLogger.getLogger(getAppName()).w(TAG, "[" + getId() + "] [postTaskToAccessSyncService] activity == null = return");
      } else if(!hasDialogBeenCreated() ) {
        WebLogger.getLogger(getAppName()).w(TAG, "[" + getId() + "] [postTaskToAccessSyncService] !msgManager.hasDialogBeenCreated()");
      } else if(!this.isResumed() ) {
        WebLogger.getLogger(getAppName()).w(TAG, "[" + getId() + "] [postTaskToAccessSyncService] !this.isResumed()");
      }
      handler.postDelayed(new Runnable() {
        @Override public void run() {
          postTaskToAccessSyncService();
        }
      }, 100);

      return;
    }
    ((ISyncServiceInterfaceActivity) activity)
        .invokeSyncInterfaceAction(new DoSyncActionCallback() {
          @Override public void doAction(IOdkSyncServiceInterface syncServiceInterface)
              throws RemoteException {
            if (syncServiceInterface != null) {
              //          WebLogger.getLogger(getAppName()).d(TAG, "[" + getId() + "] [postTaskToAccessSyncService] syncServiceInterface != null");
              final SyncStatus status = syncServiceInterface.getSyncStatus(getAppName());
              final SyncProgressEvent event = syncServiceInterface.getSyncProgressEvent(getAppName());
              if (status == SyncStatus.SYNCING) {
                syncAction = SyncActions.MONITOR_SYNCING;

                handler.post(new Runnable() {
                  @Override public void run() {
                    showProgressDialog(status, event.progressState, event.progressMessageText, event.curProgressBar, event.maxProgressBar);
                  }
                });
                return;
              }

              switch (syncAction) {
              case SYNC:
                syncServiceInterface.synchronizeWithServer(getAppName(), syncAttachmentState);
                syncAction = SyncActions.MONITOR_SYNCING;

                handler.post(new Runnable() {
                  @Override public void run() {
                    showProgressDialog(SyncStatus.NONE, null, getString(R.string.sync_starting), -1,
                        0);
                  }
                });
                break;
              case RESET_SERVER:
                syncServiceInterface.resetServer(getAppName(), syncAttachmentState);
                syncAction = SyncActions.MONITOR_SYNCING;

                handler.post(new Runnable() {
                  @Override public void run() {
                    showProgressDialog(SyncStatus.NONE, null, getString(R.string.sync_starting), -1,
                        0);
                  }
                });
                break;
              case IDLE:
                if (event.progressState == SyncProgressState.FINISHED) {
                  final SyncOverallResult result = syncServiceInterface.getSyncResult(getAppName());
                  handler.post(new Runnable() {
                    @Override public void run() {
                      showOutcomeDialog(status, result);
                    }
                  });
                }
              default:
                break;
              }
            } else {
              WebLogger.getLogger(getAppName()).w(TAG, "[" + getId() + "] [postTaskToAccessSyncService] syncServiceInterface == null");
              // The service is not bound yet so now we need to try again
              handler.postDelayed(new Runnable() {
                @Override public void run() {
                  postTaskToAccessSyncService();
                }
              }, 100);
            }
          }
        });
  }

  void updateInterface() {
    Activity activity = getActivity();
    if (activity == null || !hasDialogBeenCreated() || !this.isResumed()) {
      // we are in transition -- do nothing
      if(activity == null) {
        WebLogger.getLogger(getAppName()).w(TAG, "[" + getId() + "] [updateInterface] activity == null = return");
      } else if(!hasDialogBeenCreated() ) {
        WebLogger.getLogger(getAppName()).w(TAG, "[" + getId() + "] [updateInterface] !msgManager.hasDialogBeenCreated()");
      } else if(!this.isResumed() ) {
        WebLogger.getLogger(getAppName()).w(TAG, "[" + getId() + "] [updateInterface] !this.isResumed()");
      }
      handler.postDelayed(new Runnable() {
        @Override public void run() {
          updateInterface();
        }
      }, 100);
      return;
    }
    ((ISyncServiceInterfaceActivity) activity)
        .invokeSyncInterfaceAction(new DoSyncActionCallback() {
          @Override public void doAction(IOdkSyncServiceInterface syncServiceInterface)
              throws RemoteException {
            if (syncServiceInterface != null) {
              final SyncStatus status = syncServiceInterface.getSyncStatus(getAppName());
              final SyncProgressEvent event = syncServiceInterface.getSyncProgressEvent(getAppName());
              if (status == SyncStatus.SYNCING) {
                syncAction = SyncActions.MONITOR_SYNCING;

                handler.post(new Runnable() {
                  @Override public void run() {
                    showProgressDialog(status, event.progressState, event.progressMessageText,
                        event.curProgressBar, event.maxProgressBar);
                  }
                });
                return;
              } else if (status == SyncStatus.NONE) {
                // request completed
                syncAction = SyncActions.IDLE;
                final SyncOverallResult result = syncServiceInterface.getSyncResult(getAppName());
                handler.post(new Runnable() {
                  @Override public void run() {
                    if(event.progressState == SyncProgressState.INACTIVE) {
                      dismissDialogs();
                    } else if (event.progressState == SyncProgressState.FINISHED) {
                      showOutcomeDialog(status, result);
                    }
                  }
                });
              } else {
                // request completed
                syncAction = SyncActions.IDLE;
                final SyncOverallResult result = syncServiceInterface.getSyncResult(getAppName());
                handler.post(new Runnable() {
                  @Override public void run() {
                    if (event.progressState == SyncProgressState.FINISHED) {
                      showOutcomeDialog(status, result);
                      saveLastSyncInfo(status);
                    } else {
                      handler.postDelayed(new Runnable() {
                        @Override public void run() {
                          updateInterface();
                        }
                      }, 100);
                    }
                  }
                });
                return;
              }
            } else {
              // The service is not bound yet so now we need to try again
              handler.postDelayed(new Runnable() {
                @Override public void run() {
                  updateInterface();
                }
              }, 100);
            }
          }
        });
  }

  void syncCompletedAction(IOdkSyncServiceInterface syncServiceInterface) throws RemoteException {
    removeAnySyncNotification();
    boolean completed = syncServiceInterface.clearAppSynchronizer(getAppName());
    if (!completed) {
      throw new IllegalStateException("Could not remove AppSynchronizer for " + getAppName());
    }
    perhapsEnableButtons();
    updateInterface();
  }
  private void saveLastSyncInfo(SyncStatus status){
    if(status == SyncStatus.SYNC_COMPLETE || status == SyncStatus.SYNC_COMPLETE_PENDING_ATTACHMENTS){
      String timestamp = String.valueOf(System.currentTimeMillis());
      properties.setProperties(Collections.singletonMap(CommonToolProperties.KEY_LAST_SYNC_INFO,timestamp));
      displayLastSyncInfo();
    }
  }
  private void displayLastSyncInfo() {
    String timestamp = properties.getProperty(CommonToolProperties.KEY_LAST_SYNC_INFO);
    if (timestamp != null) {
      SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATE_TIME_FORMAT);
      String ts = sdf.format(new Date(Long.parseLong(timestamp)));
      lastSyncField.setText(ts);
    } else
      lastSyncField.setText(getResources().getString(R.string.last_sync_not_available));
  }

  private int getSyncAttachmentStateIndex(){
    String[] syncAttachmentValues = getResources().getStringArray(R.array.sync_attachment_option_values);
    for (int i = 0; i < syncAttachmentValues.length; ++i) {
      if (syncAttachmentState.name().equals(syncAttachmentValues[i])) {
        return i;
      }
    }
    return 0;
  }
  /**
   * Hooked to sync_reset_server_button's onClick in sync_launch_fragment.xml
   */
  public void onClickResetServer(View v) {
    WebLogger.getLogger(getAppName()).d(TAG, "[" + getId() + "] [onClickResetServer]");
    // ask whether to sync app files and table-level files

    if (areCredentialsConfigured(false)) {
      // show warning message
      AlertDialog.Builder msg = buildOkMessage(getString(R.string.sync_confirm_reset_app_server),
          getString(R.string.sync_reset_app_server_warning));

      msg.setPositiveButton(getString(R.string.sync_reset), new DialogInterface.OnClickListener() {
        @Override public void onClick(DialogInterface dialog, int which) {
          WebLogger.getLogger(getAppName()).d(TAG,
              "[" + getId() + "] [onClickResetServer] timestamp: " + System.currentTimeMillis());
          disableButtons();
          syncAction = SyncActions.RESET_SERVER;
          prepareForSyncAction();
        }
      });

      msg.setNegativeButton(getString(R.string.cancel), null);
      msg.show();
    }
  }

  /**
   * Hooked to syncNowButton's onClick in aggregate_activity.xml
   */
  public void onClickSyncNow(View v) {
    WebLogger.getLogger(getAppName()).d(TAG, "[" + getId() + "] [onClickSyncNow] timestamp: " + System.currentTimeMillis());
    if (areCredentialsConfigured(false)) {
      disableButtons();
      syncAction = SyncActions.SYNC;
      prepareForSyncAction();
    }
  }

  public void onClickChangeUser(View v) {
    WebLogger.getLogger(getAppName()).d(TAG, "[" + getId() + "] [onClickChangeUser] timestamp: " + System.currentTimeMillis());

    Intent i = new Intent(getActivity(), LoginActivity.class);
    i.putExtra(IntentConsts.INTENT_KEY_APP_NAME, getAppName());
    startActivity(i);
    return;
  }

  private void showProgressDialog(SyncStatus status, SyncProgressState progress, String message,
      int progressStep, int maxStep) {
    if (getActivity() == null) {
      // we are tearing down or still initializing
      return;
    }
    if (syncAction == SyncActions.MONITOR_SYNCING) {

      disableButtons();

      if (progress == null) {
        progress = SyncProgressState.INACTIVE;
      }

      int id_title;
      switch (progress) {
      case APP_FILES:
        id_title = R.string.sync_app_level_files;
        break;
      case TABLE_FILES:
        id_title = R.string.sync_table_level_files;
        break;
      case ROWS:
        id_title = R.string.sync_row_data;
        break;
      default:
        id_title = R.string.sync_in_progress;
      }

      showProgressDialog(getString(id_title), message, progressStep, maxStep);

      if (status == SyncStatus.SYNCING || status == SyncStatus.NONE) {
        handler.postDelayed(new Runnable() {
          @Override public void run() {
            updateInterface();
          }
        }, 300);
      }
    }
  }

  private void showOutcomeDialog(SyncStatus status, SyncOverallResult result) {
    if (getActivity() == null) {
      // we are tearing down or still initializing
      return;
    }
    if (syncAction == SyncActions.IDLE) {

      disableButtons();

      String message;
      int id_title;
      switch (status) {
      default:
        throw new IllegalStateException("Unexpected missing case statement");
      case
          /** earlier sync ended with socket or lower level transport or protocol error (e.g., 300's) */ NETWORK_TRANSPORT_ERROR:
        id_title = R.string.sync_communications_error;
        message = getString(R.string.sync_status_network_transport_error);
        break;
      case
          /** earlier sync ended with Authorization denied (authentication and/or access) error */ AUTHENTICATION_ERROR:
        id_title = R.string.sync_user_authorization_failure;
        message = getString(R.string.sync_status_authentication_error);
        break;
      case
          /** earlier sync ended with a 500 error from server */ SERVER_INTERNAL_ERROR:
        id_title = R.string.sync_communications_error;
        message = getString(R.string.sync_status_internal_server_error);
        break;
      case /** the server is not an ODK Server - bad client config */ SERVER_IS_NOT_ODK_SERVER:
        id_title = R.string.sync_device_configuration_failure;
        message = getString(R.string.sync_status_bad_gateway_or_client_config);
        break;
      case
          /** earlier sync ended with a 400 error that wasn't Authorization denied */ REQUEST_OR_PROTOCOL_ERROR:
        id_title = R.string.sync_communications_error;
        message = getString(R.string.sync_status_request_or_protocol_error);
        break;
      case /** no earlier sync and no active sync */ NONE:
      case /** active sync -- get SyncProgressEvent to see current status */ SYNCING:
      case
          /** error accessing or updating database */ DEVICE_ERROR:
        id_title = R.string.sync_device_internal_error;
        message = getString(R.string.sync_status_device_internal_error);
        break;
      case
          /** the server is not configured for this appName -- Site Admin / Preferences */ APPNAME_NOT_SUPPORTED_BY_SERVER:
        id_title = R.string.sync_server_configuration_failure;
        message = getString(R.string.sync_status_appname_not_supported_by_server);
        break;
      case
          /** the server does not have any configuration, or no configuration for this client version */ SERVER_MISSING_CONFIG_FILES:
        id_title = R.string.sync_server_configuration_failure;
        message = getString(R.string.sync_status_server_missing_config_files);
        break;
      case
          /** the device does not have any configuration to push to server */ SERVER_RESET_FAILED_DEVICE_HAS_NO_CONFIG_FILES:
        id_title = R.string.sync_device_configuration_failure;
        message = getString(R.string.sync_status_server_reset_failed_device_has_no_config_files);
        break;
      case
          /** while a sync was in progress, another device reset the app config, requiring a restart of
           * our sync */ RESYNC_BECAUSE_CONFIG_HAS_BEEN_RESET_ERROR:
        id_title = R.string.sync_resync_because_config_reset_error;
        message = getString(R.string.sync_status_resync_because_config_has_been_reset_error, getAppName());
        break;
      case
          /** earlier sync ended with one or more tables containing row conflicts or checkpoint rows */ CONFLICT_RESOLUTION:
        id_title = R.string.sync_conflicts_need_resolving;
        message = getString(R.string.sync_conflicts_text);
        break;
      case
          /** earlier sync ended successfully without conflicts and all row-level attachments sync'd */ SYNC_COMPLETE:
        id_title = R.string.sync_successful;
        message = getString(R.string.sync_successful_text);
        break;
      case
          /** earlier sync ended successfully without conflicts but needs row-level attachments sync'd */ SYNC_COMPLETE_PENDING_ATTACHMENTS:
        id_title = R.string.sync_complete_pending_attachments;
        message = getString(R.string.sync_complete_pending_attachments_text);
      }

      createAlertDialog(getString(id_title), message);
    }
  }
}
