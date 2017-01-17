#!/bin/bash
# Copyright (c) 2012-2016 Codenvy, S.A.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#   Tyler Jewell - Initial Implementation
#

cmd_start() {
  debug $FUNCNAME

  DISPLAY_URL=$(get_display_url)

  # If ${CHE_FORMAL_PRODUCT_NAME} is already started or booted, then terminate early.
  if container_exist_by_name $CHE_SERVER_CONTAINER_NAME; then
    CURRENT_CHE_SERVER_CONTAINER_ID=$(get_server_container_id $CHE_SERVER_CONTAINER_NAME)
    if container_is_running ${CURRENT_CHE_SERVER_CONTAINER_ID} && \
       server_is_booted ${CURRENT_CHE_SERVER_CONTAINER_ID}; then
       info "start" "${CHE_FORMAL_PRODUCT_NAME} is already running"
       info "start" "Server logs at \"docker logs -f ${CHE_SERVER_CONTAINER_NAME}\""
       info "start" "Ver: $(get_installed_version)"
       info "start" "Use: ${DISPLAY_URL}"
       info "start" "API: ${DISPLAY_URL}/swagger"
       return
    fi
  fi

  # To protect users from accidentally updating their ${CHE_FORMAL_PRODUCT_NAME} servers when they didn't mean
  # to, which can happen if CHE_VERSION=latest
  FORCE_UPDATE=${1:-"--no-force"}
  # Always regenerate puppet configuration from environment variable source, whether changed or not.
  # If the current directory is not configured with an .env file, it will initialize
  cmd_config $FORCE_UPDATE

  # Begin tests of open ports that we require
  info "start" "Preflight checks"
  cmd_start_check_ports
  text "\n"

  # Start ${CHE_FORMAL_PRODUCT_NAME}
  # Note bug in docker requires relative path, not absolute path to compose file
  info "start" "Starting containers..."
  COMPOSE_UP_COMMAND="docker_compose --file=\"${REFERENCE_CONTAINER_COMPOSE_FILE}\" -p=\"${CHE_MINI_PRODUCT_NAME}\" up -d"

  if ! debug_server; then
    COMPOSE_UP_COMMAND+=" >> \"${LOGS}\" 2>&1"
  fi

  log ${COMPOSE_UP_COMMAND}
  eval ${COMPOSE_UP_COMMAND}
  check_if_booted
}


cmd_start_check_ports() {

  # If dev mode is on, then we also need to check the debug port set by the user for availability
  if debug_server; then
    USER_DEBUG_PORT=$(get_value_of_var_from_env_file CHE_DEBUG_PORT)

    if [[ "$USER_DEBUG_PORT" = "" ]]; then
      # If the user has not set a debug port, then use the default
      CHE_DEBUG_PORT=8000
    else 
      # Otherwise, this is the value set by the user
      CHE_DEBUG_PORT=$USER_DEBUG_PORT
    fi
  fi

  text   "         port ${CHE_PORT} (http):       $(port_open ${CHE_PORT} && echo "${GREEN}[AVAILABLE]${NC}" || echo "${RED}[ALREADY IN USE]${NC}") \n"
  if debug_server; then
    text   "         port ${CHE_DEBUG_PORT} (debug):      $(port_open ${CHE_DEBUG_PORT} && echo "${GREEN}[AVAILABLE]${NC}" || echo "${RED}[ALREADY IN USE]${NC}") \n"
  fi
  if ! $(port_open ${CHE_PORT}); then
    echo ""
    error "Ports required to run $CHE_MINI_PRODUCT_NAME are used by another program."
    return 1;
  fi
  if debug_server; then
    if ! $(port_open ${CHE_DEBUG_PORT}); then
      echo ""
      error "Ports required to run $CHE_MINI_PRODUCT_NAME are used by another program."
      return 1;
    fi
  fi
}

cmd_stop() {
  debug $FUNCNAME

  if [ $# -gt 0 ]; then
    error "${CHE_MINI_PRODUCT_NAME} stop: You passed unknown options. Aborting."
    return
  fi

  info "stop" "Stopping containers..."
  if is_initialized; then
    log "docker_compose --file=\"${REFERENCE_CONTAINER_COMPOSE_FILE}\" -p=$CHE_MINI_PRODUCT_NAME stop -t ${CHE_COMPOSE_STOP_TIMEOUT} >> \"${LOGS}\" 2>&1 || true"
    docker_compose --file="${REFERENCE_CONTAINER_COMPOSE_FILE}" \
                   -p=$CHE_MINI_PRODUCT_NAME stop -t ${CHE_COMPOSE_STOP_TIMEOUT} >> "${LOGS}" 2>&1 || true
    info "stop" "Removing containers..."
    log "docker_compose --file=\"${REFERENCE_CONTAINER_COMPOSE_FILE}\" -p=$CHE_MINI_PRODUCT_NAME rm >> \"${LOGS}\" 2>&1 || true"
    docker_compose --file="${REFERENCE_CONTAINER_COMPOSE_FILE}" \
                   -p=$CHE_MINI_PRODUCT_NAME rm --force >> "${LOGS}" 2>&1 || true
  fi
}

cmd_restart() {
  debug $FUNCNAME

  FORCE_UPDATE=${1:-"--no-force"}
  info "restart" "Restarting..."
  cmd_stop
  cmd_start ${FORCE_UPDATE}
}

check_if_booted() {
  CURRENT_CHE_SERVER_CONTAINER_ID=$(get_server_container_id $CHE_SERVER_CONTAINER_NAME)
  wait_until_container_is_running 20 ${CURRENT_CHE_SERVER_CONTAINER_ID}
  if ! container_is_running ${CURRENT_CHE_SERVER_CONTAINER_ID}; then
    error "(${CHE_MINI_PRODUCT_NAME} start): Timeout waiting for ${CHE_MINI_PRODUCT_NAME} container to start."
    return 2
  fi

  info "start" "Services booting..."

  # CHE-3546 - if in development mode, then display the che server logs to STDOUT
  #            automatically kill the streaming of the log output when the server is booted
  if debug_server; then
    docker logs -f ${CHE_SERVER_CONTAINER_NAME} &
    LOG_PID=$!
  else
    info "start" "Server logs at \"docker logs -f ${CHE_SERVER_CONTAINER_NAME}\""
  fi

  wait_until_server_is_booted 60 ${CURRENT_CHE_SERVER_CONTAINER_ID}
 
  if debug_server; then
    kill $LOG_PID > /dev/null 2>&1
    info ""
  fi

  if server_is_booted ${CURRENT_CHE_SERVER_CONTAINER_ID}; then
    DISPLAY_URL=$(get_display_url)
    info "start" "Booted and reachable"
    info "start" "Ver: $(get_installed_version)"
    info "start" "Use: ${DISPLAY_URL}"
    info "start" "API: ${DISPLAY_URL}/swagger"
    if debug_server; then
      DISPLAY_DEBUG_URL=$(get_debug_display_url)
      info "start" "Debug: ${DISPLAY_DEBUG_URL}"
    fi
  else
    error "(${CHE_MINI_PRODUCT_NAME} start): Timeout waiting for server. Run \"docker logs ${CHE_SERVER_CONTAINER_NAME}\" to inspect the issue."
    return 2
  fi
}

