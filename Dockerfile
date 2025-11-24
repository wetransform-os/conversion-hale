FROM wetransform/hale-cli:6.1.6
MAINTAINER Simon Templer <simon@wetransform.to>

# add build info - see hooks/build and http://label-schema.org/
# ARG BUILD_DATE
# ARG VCS_REF
# ARG VCS_URL
# LABEL org.opencontainers.image.created=$BUILD_DATE \
#   org.opencontainers.image.source=$VCS_URL \
#   org.opencontainers.image.revision=$VCS_REF

USER root

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
