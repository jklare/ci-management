# -*- makefile -*-
# -----------------------------------------------------------------------
# Copyright 2022-2023 Open Networking Foundation (ONF) and the ONF Contributors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-FileCopyrightText: 2022 Open Networking Foundation (ONF) and the ONF Contributors
# SPDX-License-Identifier: Apache-2.0
# -----------------------------------------------------------------------

$(if $(DEBUG),$(warning ENTER))

ifdef USE-ONF-GIT-MK
  mk-include--onf-git := seen
endif

ifndef mk-include--onf-git

##--------------------##
##---]  INCLUDES  [---##
##--------------------##
include $(ONF_MAKEDIR)/git/help.mk
include $(ONF_MAKEDIR)/git/required.mk

## Special snowflake: per-repository logic loader
-include $(ONF_MAKEDIR)/git/byrepo/$(--repo-name--).mk

# Dynamic loading when targets are requested by name
include $(ONF_MAKEDIR)/git/submodules.mk

endif # mk-include--onf-git

# [EOF]
