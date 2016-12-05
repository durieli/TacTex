#!/usr/bin/python3

import fileinput

def file_prepender(filename, line):
    with open(filename, 'r+') as f:
        content = f.read()
        f.seek(0, 0)
        f.write(line.rstrip('\r\n') + '\n' + content)

GPL = """/*
 * TacTex - a power trading agent that competed in the Power Trading Agent Competition (Power TAC) www.powertac.org
 * Copyright (c) 2013-2016 Daniel Urieli and Peter Stone {urieli,pstone}@cs.utexas.edu               
 *
 *
 * This file is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This file is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
"""

def file_prepender_modified(filename, line):
    with open(filename, 'r+') as f:
        content = f.read()
        index = content.find('/*')
        f.seek(index + 3, 0)
        f.write(line.rstrip('\r\n') + '\n' + content[index + 3 : ])

GPL_MODIFIED = \
""" * TacTex - a power trading agent that competed in the Power Trading Agent Competition (Power TAC) www.powertac.org
 * Copyright (c) 2013-2016 Daniel Urieli and Peter Stone {urieli,pstone}@cs.utexas.edu               
 *
 *
 * This file is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This file is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * This file incorporates work covered by the following copyright and  
 * permission notice:  
 *"""

def indent_copyright(filename):
  with fileinput.FileInput(filename, inplace=True, backup='.bak') as f:
    START = True
    END = False
    for line in f:
      if START and line.find('/*') != -1:
        START = False
        print(line, end='')
      elif line.find('*/') != -1:
        END = True
        print(line, end='')
      elif not START and not END:
        print(line.replace('* ', '*     '), end='')
      else:
        print(line, end='')
            

with open('javafiles') as f:
    javafiles = f.read().splitlines()

with open('apachefiles') as f:
    apachefiles = f.read().splitlines()

for f in javafiles:
  if f in apachefiles:
    print (f + ' IS in apache files')
    indent_copyright(f)
    file_prepender_modified(f, GPL_MODIFIED)
  else:
    print (f + 'is NOT in apache files')
    file_prepender(f, GPL)
      


