BASEDIR=$(dirname "$0")
echo Please make sure you have run ./gradlew clean generateDescriptors before starting this script
pushd "$BASEDIR/../service"
curl -XDELETE http://localhost:9130/_/proxy/tenants/diku/modules/olf-erm-1.0.0
curl -XDELETE http://localhost:9130/_/discovery/modules/olf-erm-1.0.0/localhost-olf-erm-1.0.0
curl -XDELETE http://localhost:9130/_/proxy/modules/olf-erm-1.0.0
# ./gradlew clean generateDescriptors
curl -XPOST http://localhost:9130/_/proxy/modules -d @../service/build/resources/main/okapi/ModuleDescriptor.json
curl -XPOST http://localhost:9130/_/discovery/modules -d @../service/build/resources/main/okapi/DeploymentDescriptor.json
curl -XPOST http://localhost:9130/_/proxy/tenants/diku/modules -d '{"id": "olf-erm-1.0.0"}'
popd
