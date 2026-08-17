FROM wetransform/hale-cli:6.2.1
MAINTAINER Simon Templer <simon@wetransform.to>

# add build info - see hooks/build and http://label-schema.org/
# ARG BUILD_DATE
# ARG VCS_REF
# ARG VCS_URL
# LABEL org.opencontainers.image.created=$BUILD_DATE \
#   org.opencontainers.image.source=$VCS_URL \
#   org.opencontainers.image.revision=$VCS_REF

USER root

# Remove the OrientDB dependency and its graph stack from the image.
# OrientDB is only used by hale as temporary instance storage, which is not needed for the
# data rewrite (e.g. GML) use case this image is built for. Removing it eliminates
# CVE-2017-11467 (orientdb-core) without affecting the rewrite workflows. The hale wrapper
# bundles (eu.esdihumboldt.hale.common.*.orient-*.jar) are left in place - they are only
# exercised when the OrientDB-backed instance store is actually instantiated.
# The base image ships these jars in both the active classpath directory (/hale/lib) and a
# duplicate staging directory (/app/libs); both must be cleaned so vulnerability scanners
# (grype/trivy), which inventory files on disk regardless of the classpath, report no findings.
RUN for d in /hale/lib /app/libs; do \
      rm -f "$d"/orientdb-core-*.jar \
            "$d"/orientdb-nativeos-*.jar \
            "$d"/orient-commons-*.jar \
            "$d"/gremlin-java-*orientDB*.jar \
            "$d"/pipes-*orientDB*.jar \
            "$d"/blueprints-core-*orientDB*.jar; \
    done

# data directory - not using the base images volume because then the permissions cannot be adapted
ENV DATA_DIR /opt/data

# Install needed utilities and setup folders
RUN apt-get update -y && \
  apt-get install -y curl unzip && \
  apt-get autoremove -y && \
  apt-get clean && \
  mkdir -p /opt/data


# Fix permissions
RUN chmod -R a+rwx $DATA_DIR

USER nobody

# declare volume late so permissions apply
VOLUME /opt/data

# overwrite entrypoint
ENTRYPOINT []

CMD [/hale/bin/hale]
