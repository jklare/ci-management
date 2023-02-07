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
# SPDX-FileCopyrightText: 2022-2023 Open Networking Foundation (ONF) and the ONF Contributors
# SPDX-License-Identifier: Apache-2.0
# -----------------------------------------------------------------------

$(if $(DEBUG),$(warning ENTER))

yamllint      := $(env-clean) yamllint

## -------------------------------
## Add requirement(s) for checking
## -------------------------------
# yamllint-cfg := .yamllint
yamllint-cfg := yamllint.helm
yamllint-conf = $(wildcard $(yamllint-cfg) $(MAKEDIR)/lint/yaml/$(yamllint-cfg))
yamllint-args += $(addprefix --config-file$(space),$(yamllint-conf))

# yamllint-args := --no-warnings
# yamllint-args := --strict

yamllint-find := find .
yamllint-find += -name 'vendor' -prune
yamllint-find += -o -name '*.yaml'
yamllint-find += ! -name '\.\#*.yaml'
yamllint-find += -type f
yamllint-find += -print0

## -----------------------------------------------------------------------
## Intent: Perform a lint check on yaml sources
## -----------------------------------------------------------------------
lint lint-yaml:
	$(HIDE)$(env-clean) $(yamllint-find) \
	    | $(xargs-n1-clean) -t $(yamllint) $(yamllint-args)
#	    | xargs -0 --no-run-if-empty -t -n1 $(yamllint) $(yamllint-args)

## -----------------------------------------------------------------------
## Intent: Display command help
## -----------------------------------------------------------------------
help::
	@echo "  lint-yaml           Syntax check yaml sources (yamllint)"

$(if $(DEBUG),$(warning ENTER))

# [EOF]
