# -*- makefile -*-
## -----------------------------------------------------------------------
# Copyright 2017-2023 Open Networking Foundation
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
## -----------------------------------------------------------------------

## usage:   {target} : venv-req
# venv : .venv/bin/activate requirements.txt
venv-dep : .venv/bin/activate
venv-req : .venv/bin/activate requirements.txt

.venv/bin/activate:
	virtualenv -p python3 $@
	source ./$@/bin/activate \
	   pip install --upgrade pip \
	source ./$@/bin/activate \
	   pip install --upgrade setuptools \
	[[ -r requirements.txt ]]       \
	    && source ./$@/bin/activate \
	    && python -m pip install -r requirements.txt

# [EOF]
