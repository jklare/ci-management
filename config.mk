# -*- makefile -*-
# -----------------------------------------------------------------------
# Copyright 2023 Open Networking Foundation (ONF) and the ONF Contributors
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
# -----------------------------------------------------------------------
# https://gerrit.opencord.org/plugins/gitiles/onf-make
# ONF.makefiles.include.version = 1.1
# ONF.confg.mk                  = 1.5
# -----------------------------------------------------------------------

--repo-name-- := ci-management
--repo-name-- ?= $(error --repo-name--= is required)

##--------------------------------##
##---]  Disable lint targets  [---##
##--------------------------------##
# NO-LINT-DOC8      := true
# NO-LINT-GOLANG    := true
NO-LINT-GROOVY      := true#               # Note[1]
# NO-LINT-JJB       := true#               # Note[2]
# NO-LINT-JSON      := true#               # Note[1]
NO-LINT-MAKEFILE    := true#               # Note[1]
NO-LINT-REUSE       := true                # License check
NO-LINT-ROBOT       := true
NO-LINT-SHELL       := true#               # Note[1]
# NO-LINT-TOX       := true#               # Note[1]
NO-LINT-YAML        := true#               # Note[1]

# NO-LINT-FLAKE8    := true#               # Note[1]
NO-LINT-PYTHON      := true#               # Note[1]
# NO-LINT-PYLINT    := true#               # Note[1]

# Note[1] - A boatload of source to cleanup prior to enable.
# Note[2] - No sources available

##---------------------------------##
##---] Conditional make logic  [---##
##---------------------------------##
# USE-ONF-DOCKER-MK      := true
# USE-ONF-GERRIT-MK      := true
# USE-ONF-GIT-MK         := true
USE-ONF-JJB-MK           := true
# USE-VOLTHA-RELEASE-MK  := true

##----------------------##
##---]  Debug Mode  [---##
##----------------------##
# export DEBUG           := 1      # makefile debug
# export DISTUTILS_DEBUG := 1      # verbose: pip
# export DOCKER_DEBUG    := 1      # verbose: docker
# export VERBOSE         := 1      # makefile debug

##-----------------------------------##
##---]  JJB/Jenkins Job Builder  [---##
##-----------------------------------##
JJB_VERSION   ?= 2.8.0
JOBCONFIG_DIR ?= job-configs

##---------------------------------##
##---]  Filesystem exclusions  [---##
##---------------------------------##
onf-excl-dirs := $(null)        # make clean: dirs=
onf-excl-dirs += .venv#         # $(venv-name)
onf-excl-dirs += vendor#        # golang / voltha*-go
onf-excl-dirs += patches#       # voltha docs - python upgrade
onf-excl-dirs += .tox           # also a python dependency

ifeq ($(--repo-name--),voltha-docs)
  lint-doc8-excl += '_build'
endif

onf-excl-dirs ?= $(error onf-excl-dirs= is required)

##-----------------------------##
##---]  Feature Detection  [---##
##-----------------------------##
# [TODO] include makefiles/features/include.mk
# [TODO] All logic below can migrate there.

$(if $(filter %ci-management,$(--repo-name--)),\
  $(eval --REPO-IS-CI-MANAGEMENT-- := true)\
)
$(if $(filter %voltha-docs,$(--repo-name--)),\
  $(eval --REPO-IS-VOLTHA-DOCS-- := true)\
)

# create makefiles/config/byrepo/{--repo-name--}.mk for one-off snowflakes ?
# $(if $(wildcard docker),$(eval USE-ONF-DOCKER-MK := true))

##-------------------------##
##---]  Derived Flags  [---##
##-------------------------##
ifdef --REPO-IS-CI-MANAGEMENT--
  USE-ONF-JJB := true

  onf-excl-dirs += global-jjb
  onf-excl-dirs += lf-ansible
  onf-excl-dirs += packer
endif

ifdef --REPO-IS-VOLTHA-DOCS--
  onf-excl-dirs += _build
  onf-excl-dirs += repos
endif

ifdef NO-LINT-PYTHON
  NO-LINT-FLAKE8 := true
  NO-LINT-PYLINT := true
endif

ifndef USE-ONF-JJB
  NO-LINT-JJB := true
endif

onf-excl-dirs := $(sort $(strip $(onf-excl-dirs)))

# --------------------------------------------------------------------
# Repository specific values
# --------------------------------------------------------------------
sterile-dirs += archives

# submodules
# sterile-dirs += global-jjb
# sterile-dirs += lf-ansible
# sterile-dirs += packer

# [TODO]#
#  --------------------------------------------------------------------
#   o two distinct makefiles/ directories are needed, one for onf-make
#   o second for repository specific makefile configs and logic.
#   o Two independent vars specify path:
#       ONF_MAKEDIR = library makefiles
#       MAKEDIR     = repository specific content
#   o Conditional repository testing above can crush down all the
#     "if-this-repository-is-X-do-Y' logic above intoL
#     include $(MAKEDIR)/config.mk   # repo:$(--repo-name--)
#  --------------------------------------------------------------------

# [EOF]
