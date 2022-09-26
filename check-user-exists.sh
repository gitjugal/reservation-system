my_key=$( cat /tmp/client.pub )
if grep "$my_key" $HOME/.ssh/authorized_keys ; then
  return true
else
  return false
fi;