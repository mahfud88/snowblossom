#!/bin/bash
if [[ $EUID -ne 0 ]]; then
   echo "This script requires root!"
   exit 1
fi

# exit upon any error
set -eu

snowblossom_home=/var/snowblossom
echo "Building snowblossom from source in $snowblossom_home"

# install openjdk-8-jdk and bazel
echo "deb [arch=amd64] http://storage.googleapis.com/bazel-apt stable jdk1.8" > /etc/apt/sources.list.d/snowblossom-bazel.list
wget -qO - https://bazel.build/bazel-release.pub.gpg | apt-key add -
apt-get update
apt-get -yq install git openjdk-8-jdk bazel git

# download
mkdir -p $snowblossom_home
cd "$snowblossom_home"
git clone "https://github.com/snowblossomcoin/snowblossom.git" source

# build
cd source
bazel build :all

# setup simple helpful run scrips?
cd "$snowblossom_home"
cp source/example/configs ./
echo "#!/bin/bash\nsource/bazel-bin/SnowBlossomNode configs/node.conf" > node.sh
echo "#!/bin/bash\nsource/bazel-bin/SnowBlossomClient configs/client.conf $1 $2 $3" > client.sh
echo "#!/bin/bash\nsource/bazel-bin/SnowBlossomClient configs/miner.conf" > miner.sh

echo "Done."
echo "You can manually run:  node.sh,  client.sh,  miner.sh"
