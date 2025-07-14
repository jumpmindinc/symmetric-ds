/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
#include "stdio.h"
#include "ibase.h"
#include "ib_util.h"

static char backslash_chr = '\\';
static char quote_chr = '"';
static char *empty_str = "";

typedef struct blob {
	short	(*blob_get_segment) ();
	void	*blob_handle;
	long	blob_number_segments;
	long	blob_max_segment;
	long	blob_total_length;
	void	(*blob_put_segment) ();
} *BLOBCALLBACK;

char *sym_escape(char *str);
char *sym_hex(BLOBCALLBACK blob);
