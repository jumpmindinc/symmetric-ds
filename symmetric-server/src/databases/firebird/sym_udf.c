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
#include "sym_udf.h"

char *sym_escape(char *str)
{
   int len = 0, count = 0;
   int i, j;
   char *result;

   if (str != NULL)
   {
      for (; str[len] != NULL; len++)
      {
         if (str[len] == quote_chr || str[len] == backslash_chr)
         {
            count++;
         }
      }
   }

   result = (char *) ib_util_malloc(count + len + 1);
   if (result == NULL)
   {
      return NULL;
   }

   for (i = 0, j = 0; i < len; i++, j++)
   {
      if (str[i] == quote_chr || str[i] == backslash_chr)
      {
         result[j] = backslash_chr;
         result[++j] = str[i];
      }
      else
      {
         result[j] = str[i];
      }
   }
   result[j] = NULL;

   return result;
}

/*
 * Code for reading the BLOB was kindly borrowed from FreeAdhocUDF, written by
 * adhoc dataservice GmbH, Christoph Theuring ct / Peter Mandrella pm / Georg Horn gh
 */
char *sym_hex(BLOBCALLBACK blob)
{
   char *result, *hex_result;
   long hex_result_size;
   ISC_USHORT bytes_read;
   long total_length;
   long total_bytes_read;
   long i, j;

   bytes_read = 0;
   total_bytes_read = 0;

   if (blob->blob_handle == NULL)
   {
      result = (char *) malloc(1);
      if (!result)
      {
          return NULL;
      }
   }
   else
   {
      total_length = blob->blob_total_length;
      result = (char *) malloc(total_length + 1);
      if (!result)
      {
          return NULL;
      }
      memset(result, 0, total_length + 1);
      for (i = 0; i < blob->blob_number_segments; ++i)
      {
         bytes_read = 0;
         blob->blob_get_segment(blob->blob_handle, (char*)result + total_bytes_read, total_length - total_bytes_read, &bytes_read);
         total_bytes_read += bytes_read;
      }
   }
   result[total_bytes_read] = '\0';
  
   hex_result_size = (total_bytes_read * 2) + 1;
   hex_result = (char *) ib_util_malloc(hex_result_size);
   if (!hex_result)
   {
      return NULL;
   }
   memset(hex_result, 0, hex_result_size);
   for (i = 0, j = 0; i < total_bytes_read; i++, j += 2)
   {
     sprintf(hex_result + j, "%02x", (unsigned char)result[i]); 
   }

   hex_result[hex_result_size] = '\0';    
  
   free(result);

   return hex_result;
}
