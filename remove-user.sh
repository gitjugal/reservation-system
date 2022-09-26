#Removed user key if exists
my_key=$( cat /tmp/client.pub )
if test -f $HOME/.ssh/authorized_keys; then
  if grep -v "$my_key" $HOME/.ssh/authorized_keys > $HOME/.ssh/tmp; then
    cat $HOME/.ssh/tmp > $HOME/.ssh/authorized_keys && rm $HOME/.ssh/tmp;
  else
    rm $HOME/.ssh/authorized_keys && rm $HOME/.ssh/tmp;
  fi;
fi